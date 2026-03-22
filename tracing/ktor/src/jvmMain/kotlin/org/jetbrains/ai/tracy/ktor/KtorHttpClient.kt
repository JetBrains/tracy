/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.ktor

import org.jetbrains.ai.tracy.core.TracingManager
import org.jetbrains.ai.tracy.core.adapters.LLMTracingAdapter
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpResponse
import org.jetbrains.ai.tracy.core.http.protocol.asRequestBody
import org.jetbrains.ai.tracy.core.http.protocol.asRequestView
import io.ktor.client.*
import io.ktor.client.plugins.api.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.io.Buffer
import kotlinx.io.InternalIoApi
import kotlinx.io.readByteArray
import kotlinx.io.readString
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.serializer
import mu.KotlinLogging
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpRequestBody
import org.jetbrains.ai.tracy.core.http.parsers.SseParser
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpResponseBody
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.starProjectedType

/**
 * Instruments a Ktor [HttpClient] with OpenTelemetry tracing for LLM provider API calls.
 *
 * This function configures automatic tracing for HTTP requests made through the Ktor client to supported
 * LLM providers. The provided [LLMTracingAdapter] determines how requests and responses are parsed and
 * traced according to the specific provider's API format. Supports both streaming and non-streaming requests,
 * handles multipart form data, and captures rich telemetry including token usage, model parameters, and errors.
 *
 * ## Supported Adapters
 * - **OpenAILLMTracingAdapter**: For OpenAI API (chat completions, responses, images)
 * - **AnthropicLLMTracingAdapter**: For Anthropic Claude API
 * - **GeminiLLMTracingAdapter**: For Google Gemini API
 *
 * ## Use Cases
 *
 * ### Basic OpenAI Chat Completion
 * ```kotlin
 * val client = instrument(HttpClient(), OpenAILLMTracingAdapter())
 * val response = client.post("https://api.openai.com/v1/chat/completions") {
 *     header("Authorization", "Bearer $apiKey")
 *     setBody("""
 *         {
 *             "messages": [
 *                 {
 *                     "role": "user",
 *                     "content": "greet me and introduce yourself"
 *                 }
 *             ],
 *             "model": "gpt-4o-mini"
 *         }
 *     """.trimIndent())
 * }
 * // Request and response are automatically traced
 * ```
 *
 * ### Streaming Request
 * ```kotlin
 * val client = instrument(HttpClient(), OpenAILLMTracingAdapter())
 * val response = client.post("https://api.openai.com/v1/chat/completions") {
 *     header("Authorization", "Bearer $apiKey")
 *     header("Accept", "text/event-stream")
 *     setBody("""
 *         {
 *             "messages": [{"role": "user", "content": "hello world"}],
 *             "model": "gpt-4o-mini",
 *             "stream": true
 *         }
 *     """.trimIndent())
 * }
 * response.bodyAsChannel() // Streaming data is captured and traced
 * ```
 *
 * ### Multipart Form Data (Image Edits)
 * ```kotlin
 * val client = instrument(HttpClient(), OpenAILLMTracingAdapter())
 * val response = client.post("https://api.openai.com/v1/images/edits") {
 *     val body = MultiPartFormDataContent(formData {
 *         append("model", "gpt-image-1")
 *         append("prompt", "Remove all dogs from the image")
 *         append("image", imageBytes, Headers.build {
 *             append(HttpHeaders.ContentType, "image/png")
 *             append(HttpHeaders.ContentDisposition, "filename=\"image.png\"")
 *         })
 *     })
 *     header("Authorization", "Bearer $apiKey")
 *     contentType(ContentType.MultiPart.FormData.withParameter("boundary", body.boundary))
 *     setBody(body)
 * }
 * // Multipart form data is parsed and traced
 * ```
 *
 * ### Using with Serializable Request Objects
 * ```kotlin
 * @Serializable
 * data class Request(
 *     val messages: List<Message>,
 *     val model: String,
 * )
 *
 * val client = instrument(HttpClient {
 *     install(ContentNegotiation) {
 *         json(Json { prettyPrint = true })
 *     }
 * }, OpenAILLMTracingAdapter())
 *
 * val response = client.post("https://api.openai.com/v1/chat/completions") {
 *     header("Authorization", "Bearer $apiKey")
 *     setBody<Request>(Request(
 *         messages = listOf(Message(role = "user", content = "Introduce yourself")),
 *         model = "gpt-4o-mini"
 *     ))
 * }
 * // Serializable objects are automatically traced
 * ```
 *
 * ## Notes
 * - Tracing can be controlled globally via `TracingManager.isTracingEnabled`.
 * - The response body is **peeked** (not consumed), so it remains available for further processing.
 * - Content capture policies [TracingManager.contentCapturePolicy] can be configured to redact sensitive data.
 * - Error responses are automatically captured with error status and messages.
 *
 * @param client The [HttpClient] instance to be configured for tracing
 * @param adapter The [LLMTracingAdapter] specifying which LLM provider adapter to use for tracing
 *
 * @see TracingManager
 * @see TracingManager.traceSensitiveContent
 */
fun instrument(client: HttpClient, adapter: LLMTracingAdapter): HttpClient {
    return client.config {
        TracingPlugin(adapter).setup(this)
    }
}

private class TracingPlugin(private val adapter: LLMTracingAdapter) {
    private val httpSpanKey = AttributeKey<Span>("HttpSpanKey")
    private val tracingEnabledKey = AttributeKey<Boolean>("TracingEnabledKey")

    @OptIn(InternalAPI::class, InternalIoApi::class)
    fun setup(config: HttpClientConfig<*>) {
        val tracer = TracingManager.tracer

        // duplicate plugins are ignored by the API implementation
        config.install(createClientPlugin("NetworkParamsPlugin") {
            onRequest { request, _ ->
                val tracingEnabled = TracingManager.isTracingEnabled
                request.attributes.put(tracingEnabledKey, tracingEnabled)
                if (!tracingEnabled) {
                    return@onRequest
                }

                val span = tracer.spanBuilder(adapter.getSpanName()).startSpan()

                span.makeCurrent().use {
                    request.attributes.put(httpSpanKey, span)

                    val contentType = request.contentType()?.toContentType()
                    val charset = request.contentType()?.charset() ?: Charsets.UTF_8
                    val bodyContent = request.copyBodyContent()

                    // parse the request body and make a request view with it
                    val requestBody = when {
                        (bodyContent != null) && (contentType != null) -> try {
                            bodyContent.asRequestBody(contentType, charset)
                        } catch(e: Exception) {
                            logger.warn(e) { "Failed to parse request body for tracing; request body will be empty" }
                            null
                        }
                        else -> {
                            if (request.body !is EmptyContent && bodyContent == null) {
                                // this case means that the body is present but couldn't be parsed correctly
                                logger.warn("Either body or content type are null; request body will be empty")
                            }
                            null
                        }
                    } ?: TracyHttpRequestBody.Empty

                    val tracyRequest = requestBody.asRequestView(
                        contentType = contentType,
                        url = request.url.toProtocolUrl(),
                        method = request.method.value,
                    )
                    adapter.registerRequest(span, tracyRequest)
                }
            }

            // processes any response (i.e., application/json) except for SSE responses (i.e., text/stream-event)
            onResponse { response ->
                val enabled = response.call.request.attributes[tracingEnabledKey]
                if (!enabled) {
                    return@onResponse
                }
                // don't register SSE responses here
                val mimeType = response.contentType()?.toContentType()?.mimeType
                if (mimeType == "text/event-stream") {
                    return@onResponse
                }
                val span = response.call.request.attributes.getOrNull(httpSpanKey)
                    ?: return@onResponse

                adapter.registerResponse(span, response = response.asResponseView())
                span.end()
            }

            // processes ONLY SSE responses (i.e., text/stream-event)
            transformResponseBody { response, content, typeInfo ->
                val enabled = response.call.request.attributes[tracingEnabledKey]
                if (!enabled) {
                    return@transformResponseBody null
                }
                // skip non-SSE responses
                val mimeType = response.contentType()?.toContentType()?.mimeType
                if (mimeType != "text/event-stream") {
                    return@transformResponseBody null
                }
                val span = response.call.request.attributes.getOrNull(httpSpanKey)
                    ?: return@transformResponseBody null


                adapter.registerResponse(span, response = response.asResponseView())

                // trace SSE events and register them in the adapter one by one
                val originalBody: ByteReadChannel = content
                val tracingChannel = ByteChannel(autoFlush = true)

                val url = response.request.url.toProtocolUrl()
                val sseParser = SseParser { event ->
                    // trace SSE event in adapter
                    adapter.registerResponseStreamEvent(span, url, event)
                }

                CoroutineScope(response.coroutineContext).launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (!originalBody.isClosedForRead) {
                            val bytesRead = originalBody.readAvailable(buffer, 0, buffer.size)
                            if (bytesRead == -1) {
                                break
                            }
                            if (bytesRead > 0) {
                                sseParser.feed(
                                    input = buffer.decodeToString(0, bytesRead)
                                )
                                tracingChannel.writeFully(buffer, 0, bytesRead)
                                tracingChannel.flush()
                            }
                        }
                    } catch (e: Exception) {
                        span.setStatus(StatusCode.ERROR)
                        span.recordException(e)
                        if (!tracingChannel.isClosedForWrite) {
                            tracingChannel.close(e)
                        }
                    } finally {
                        span.end()
                        if (!tracingChannel.isClosedForWrite) {
                            tracingChannel.close()
                        }
                    }
                }

                return@transformResponseBody when {
                    typeInfo.type == ByteReadChannel::class -> tracingChannel
                    else -> null
                }
            }
        })
    }

    @OptIn(InternalIoApi::class, InternalAPI::class)
    private suspend fun HttpResponse.asResponseView(): TracyHttpResponse {
        val response = this
        val responseBody = when (response.contentType()?.withoutParameters()) {
            ContentType.Application.Json -> try {
                // peek the response body to avoid consuming the underlying channel
                val responseString = run {
                    // NOTE: we must first peek and only then await.
                    // otherwise there are cases when an empty body gets peeked
                    val peeked = response.rawContent.readBuffer.peek()
                    response.rawContent.awaitContent(Int.MAX_VALUE)
                    peeked.request(Long.MAX_VALUE)
                    val buffer = Buffer()
                    buffer.write(peeked, peeked.buffer.size)
                    buffer.readString()
                }
                val json = Json.parseToJsonElement(responseString).jsonObject
                TracyHttpResponseBody.Json(json)
            } catch (exception: Exception) {
                logger.trace("Error while parsing response body", exception)
                TracyHttpResponseBody.Empty
            }
            ContentType.Text.EventStream -> TracyHttpResponseBody.EventStream
            else -> TracyHttpResponseBody.Empty
        }

        return TracyHttpResponseView(response, body = responseBody)
    }

    /**
     * Helper function to serialize `@Serializable` objects with an unknown type
     */
    @OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
    private fun serializeToJson(obj: Any): String? {
        return try {
            val kClass = obj::class
            if (kClass.hasAnnotation<Serializable>()) {
                JSON_CONFIG.encodeToString(Json.serializersModule.serializer(kClass.starProjectedType), obj)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun HttpRequestBuilder.copyBodyContent(): ByteArray? {
        // first, attempt to parse the body directly.
        // if fails, check if the underlying type is serializable
        val bytes = when (val body = this.body) {
            is MultiPartFormDataContent -> {
                val ch = ByteChannel()
                try {
                    body.writeTo(ch)
                    ch.readRemaining().readByteArray()
                } finally {
                    ch.close()
                }
            }

            is TextContent -> body.text.toByteArray()
            is String -> body.toByteArray()
            is EmptyContent -> null
            else -> null
        }
        if (bytes != null) {
            return bytes
        }

        val bodyType = this.bodyType?.type
        return when {
            bodyType != null && bodyType.hasAnnotation<Serializable>() -> {
                serializeToJson(body)?.toByteArray()
            }

            else -> null
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}

        private val JSON_CONFIG = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
