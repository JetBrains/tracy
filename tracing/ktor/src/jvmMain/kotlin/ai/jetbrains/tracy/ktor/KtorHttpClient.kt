/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package ai.jetbrains.tracy.ktor

import ai.jetbrains.tracy.core.TracingManager
import ai.jetbrains.tracy.core.adapters.LLMTracingAdapter
import ai.jetbrains.tracy.core.http.protocol.*
import ai.jetbrains.tracy.core.http.protocol.ContentType
import io.ktor.client.*
import io.ktor.client.plugins.api.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.http.Url
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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.serializer
import mu.KotlinLogging
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.starProjectedType
import ai.jetbrains.tracy.core.http.protocol.RequestBody as TracyRequestBody

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
    private val isStreamingRequestKey = AttributeKey<Boolean>("IsStreamingRequestKey")

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

                val span = tracer.spanBuilder("http-client-span").startSpan()

                span.makeCurrent().use {
                    request.attributes.put(httpSpanKey, span)

                    val contentType = request.contentType()?.toContentType()
                    val charset = request.contentType()?.charset() ?: Charsets.UTF_8
                    val bodyContent = request.copyBodyContent()

                    // parse request body and make a request view with it
                    val requestBody = when {
                        (bodyContent != null) && (contentType != null) -> bodyContent.asRequestBody(contentType, charset)
                        else -> {
                            logger.warn("Either body or content type are null, defaulting to empty request body")
                            null
                        }
                    }

                    val req = if (requestBody != null && contentType != null) {
                        requestBody.asRequestView(contentType, url = request.url.toProtocolUrl())
                    } else {
                        null
                    }

                    if (req != null) {
                        request.attributes.put(isStreamingRequestKey, value = adapter.isStreamingRequest(req))
                        adapter.registerRequest(span, req)
                    }
                }
            }

            onResponse { response ->
                val enabled = response.call.request.attributes[tracingEnabledKey]
                if (!enabled) return@onResponse
                val isStreamingRequest = response.call.request.attributes.getOrNull(isStreamingRequestKey)
                    ?: return@onResponse
                val span = response.call.request.attributes.getOrNull(httpSpanKey)
                    ?: return@onResponse
                if (isStreamingRequest) return@onResponse
                val body = try {
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
                    Json.parseToJsonElement(responseString).jsonObject
                } catch (exception: Exception) {
                    logger.trace("Error while parsing response body", exception)
                    JsonObject(emptyMap())
                }

                adapter.registerResponse(span, response = response.asResponseView(body))
                span.end()
            }

            transformResponseBody { response, content, typeInfo ->
                val enabled = response.call.request.attributes[tracingEnabledKey]
                if (!enabled) return@transformResponseBody null

                val isStreamingRequest = response.call.request.attributes.getOrNull(isStreamingRequestKey)
                    ?: return@transformResponseBody null
                val span = response.call.request.attributes.getOrNull(httpSpanKey)
                    ?: return@transformResponseBody null

                if (!isStreamingRequest) {
                    return@transformResponseBody null
                }

                val body = JsonObject(mapOf("stream" to JsonPrimitive(true)))
                // registering response attributes into span
                adapter.registerResponse(span, response = response.asResponseView(body))

                val originalBody: ByteReadChannel = content
                val tracingChannel = ByteChannel(autoFlush = true)
                val capturedText = StringBuilder()

                CoroutineScope(response.coroutineContext).launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (!originalBody.isClosedForRead) {
                            val bytesRead = originalBody.readAvailable(buffer, 0, buffer.size)
                            if (bytesRead == -1) break
                            if (bytesRead > 0) {
                                capturedText.append(buffer.decodeToString(0, bytesRead))
                                tracingChannel.writeFully(buffer, 0, bytesRead)
                                tracingChannel.flush()
                            }
                        }
                    } catch (e: Exception) {
                        span.setStatus(StatusCode.ERROR)
                        span.recordException(e)
                        if (!tracingChannel.isClosedForWrite) tracingChannel.close(e)
                    } finally {
                        try {
                            adapter.handleStreaming(
                                span = span,
                                url = response.request.url.toProtocolUrl(),
                                events = capturedText.toString()
                            )
                        } finally {
                            span.end()
                            if (!tracingChannel.isClosedForWrite) tracingChannel.close()
                        }
                    }
                }
                if (typeInfo.type != ByteReadChannel::class) null else tracingChannel
            }
        })
    }

    private fun TracyRequestBody.asRequestView(
        contentType: ContentType,
        url: ai.jetbrains.tracy.core.http.protocol.Url
    ): Request {
        val requestBody = this
        return object : Request {
            override val body = requestBody
            override val contentType = contentType
            override val url = url
        }
    }

    private fun HttpResponse.asResponseView(body: JsonObject): Response {
        val response = this
        return object : Response {
            override val contentType = response.contentType()?.toContentType()
            override val code = response.status.value
            override val body = ResponseBody.Json(body)
            override val url = response.request.url.toProtocolUrl()
        }
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

private fun URLBuilder.toProtocolUrl() =
    ai.jetbrains.tracy.core.http.protocol.Url(protocol.name, host, pathSegments)

private fun Url.toProtocolUrl() =
    ai.jetbrains.tracy.core.http.protocol.Url(protocol.name, host, segments)
