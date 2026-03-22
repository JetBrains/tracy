/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.core.interceptors

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import mu.KotlinLogging
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import okio.BufferedSource
import okio.buffer
import org.jetbrains.ai.tracy.core.adapters.LLMTracingAdapter
import org.jetbrains.ai.tracy.core.http.protocol.*
import org.jetbrains.ai.tracy.core.TracingManager
import okhttp3.Request as OkHttpRequest
import okhttp3.Response as OkHttpResponse
import okhttp3.ResponseBody as OkHttpResponseBody

/**
 * Instruments an [OkHttpClient] with OpenTelemetry tracing for LLM provider API calls,
 * returning a cloned and patched instance of the provided [OkHttpClient].
 *
 * This function adds automatic tracing capabilities to an OkHttp client by injecting an
 * [OpenTelemetryOkHttpInterceptor] configured with the provided [LLMTracingAdapter]. All HTTP
 * requests made through the instrumented client to LLM provider APIs will be automatically traced,
 * including request/response bodies, token usage, model parameters, tool calls, and errors.
 * Supports both streaming and non-streaming requests.
 *
 * This is a lower-level instrumentation function that works with raw OkHttp clients. For
 * provider-specific clients (OpenAI, Anthropic, Gemini), consider using the provider-specific
 * `instrument()` functions instead.
 *
 * ## Use Cases
 *
 * ### Basic OpenAI API Request
 * ```kotlin
 * TracingManager.setSdk(configureOpenTelemetrySdk(ConsoleExporterConfig()))
 * TracingManager.traceSensitiveContent()
 *
 * val apiToken = System.getenv("OPENAI_API_KEY")
 * val requestBodyJson = buildJsonObject {
 *     put("model", JsonPrimitive("gpt-4o-mini"))
 *     put("messages", buildJsonArray {
 *         add(buildJsonObject {
 *             put("role", JsonPrimitive("user"))
 *             put("content", JsonPrimitive("Generate polite greeting and introduce yourself"))
 *         })
 *     })
 *     put("temperature", JsonPrimitive(1.0))
 * }
 *
 * val client = OkHttpClient()
 * val instrumentedClient = instrument(client, OpenAILLMTracingAdapter())
 *
 * val requestBody = Json { prettyPrint = true }
 *     .encodeToString(requestBodyJson)
 *     .toRequestBody("application/json".toMediaType())
 *
 * val request = Request.Builder()
 *     .url("https://api.openai.com/v1/chat/completions")
 *     .addHeader("Authorization", "Bearer $apiToken")
 *     .addHeader("Content-Type", "application/json")
 *     .post(requestBody)
 *     .build()
 *
 * instrumentedClient.newCall(request).execute().use { response ->
 *     println("Result: ${response.body?.string()}")
 * }
 *
 * TracingManager.flushTraces()
 * ```
 *
 * ### Using with Different Providers
 * ```kotlin
 * // OpenAI
 * val openAiClient = instrument(OkHttpClient(), OpenAILLMTracingAdapter())
 *
 * // Anthropic
 * val anthropicClient = instrument(OkHttpClient(), AnthropicLLMTracingAdapter())
 *
 * // Gemini
 * val geminiClient = instrument(OkHttpClient(), GeminiLLMTracingAdapter())
 * ```
 *
 * ### Streaming Requests
 * ```kotlin
 * val client = instrument(OkHttpClient(), OpenAILLMTracingAdapter())
 * val request = Request.Builder()
 *     .url("https://api.openai.com/v1/chat/completions")
 *     .addHeader("Authorization", "Bearer $apiToken")
 *     .post(streamingRequestBody)
 *     .build()
 *
 * client.newCall(request).execute().use { response ->
 *     response.body?.source()?.let { source ->
 *         // Read streaming response
 *         while (!source.exhausted()) {
 *             val line = source.readUtf8Line()
 *             // Process streaming data
 *         }
 *     }
 * }
 * // Streaming data is automatically captured and traced
 * ```
 *
 * ## Notes
 * - This function is **idempotent**: calling `instrument()` multiple times on the same client
 *   will not result in duplicate interceptors.
 * - Tracing can be controlled globally via `TracingManager.isTracingEnabled`.
 * - **The original client is not modified; a new client instance with instrumentation is returned**.
 * - Content capture policies [org.jetbrains.ai.tracy.core.TracingManager.contentCapturePolicy] can be configured to redact sensitive data.
 * - Error responses are automatically captured with error status and messages.
 *
 * @param client The OkHttp client to instrument
 * @param adapter The [LLMTracingAdapter] specifying which LLM provider adapter to use for tracing
 * @return **A new [OkHttpClient] instance** with OpenTelemetry tracing enabled (i.e., the initial [client] remains **unmodified**)
 *
 * @see org.jetbrains.ai.tracy.core.TracingManager
 * @see org.jetbrains.ai.tracy.core.TracingManager.traceSensitiveContent
 */
fun instrument(client: OkHttpClient, adapter: LLMTracingAdapter): OkHttpClient {
    val clientBuilder = client.newBuilder()

    val interceptor = OpenTelemetryOkHttpInterceptor(adapter)
    patchInterceptorsInplace(clientBuilder.interceptors(), interceptor)

    return clientBuilder.build()
}

/**
 * Patches the OpenAI-compatible client by injecting a custom interceptor into its internal HTTP client
 * **in-place**.
 *
 * This method modifies the internal structure of the provided OpenAI-like client to replace its HTTP client interceptors
 * with the specified interceptor.
 * Supports OpenAI-compatible (**in terms of internal class structure**) clients.
 *
 * @param client The instance of the OpenAI-compatible client to patch.
 * @param interceptor The interceptor to be injected into the internal HTTP client of the OpenAI-compatible client.
 */
fun <T> patchOpenAICompatibleClient(client: T, interceptor: Interceptor) {
    val clientOptions = getFieldValue(client as Any, "clientOptions")
    val originalHttpClient = getFieldValue(clientOptions, "originalHttpClient")

    val okHttpHolder = if (originalHttpClient::class.simpleName == "OkHttpClient") {
        originalHttpClient
    } else {
        getFieldValue(originalHttpClient, "httpClient")
    }

    val okHttpClient = getFieldValue(okHttpHolder, "okHttpClient") as OkHttpClient

    // add a given interceptor if the current list of interceptors doesn't contain it already
    val updatedInterceptors = patchInterceptors(okHttpClient.interceptors, interceptor)
    setFieldValue(okHttpClient, "interceptors", updatedInterceptors)
}

internal fun getFieldValue(instance: Any, fieldName: String): Any {
    var cls: Class<*>? = instance.javaClass
    while (cls != null) {
        try {
            val field = cls.getDeclaredField(fieldName)
            field.isAccessible = true
            return field.get(instance) ?: throw IllegalStateException("Field '$fieldName' is null")
        } catch (_: NoSuchFieldException) {
            cls = cls.superclass
        }
    }
    throw NoSuchFieldException("Field '$fieldName' not found in ${instance.javaClass.name}")
}

internal fun setFieldValue(instance: Any, fieldName: String, value: Any?) {
    var cls: Class<*>? = instance.javaClass
    while (cls != null) {
        try {
            val field = cls.getDeclaredField(fieldName)
            field.isAccessible = true

            if (value == null && field.type.isPrimitive) {
                throw IllegalArgumentException("Cannot set primitive field '$fieldName' to null")
            }

            field.set(instance, value)
            return
        } catch (_: NoSuchFieldException) {
            cls = cls.superclass
        }
    }
    throw NoSuchFieldException("Field '$fieldName' not found in ${instance.javaClass.name}")
}

/**
 * Intercepts OkHttp calls and traces them using the provided [adapter].
 *
 * TODO: extract to a separate `OkHttp` module.
 */
class OpenTelemetryOkHttpInterceptor(
    private val adapter: LLMTracingAdapter,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): OkHttpResponse {
        if (!TracingManager.isTracingEnabled) {
            return chain.proceed(chain.request())
        }

        val tracer = TracingManager.tracer

        val span = tracer.spanBuilder(adapter.getSpanName()).startSpan()
        // whether the response content type is `text/event-stream`
        var isStreamingResponse = false

        span.makeCurrent().use { _ ->
            try {
                // register request
                val (bodyContent, request) = chain.request().withCopiedBodyContent()

                // building request view
                val mediaType = request.body?.contentType()
                val tracyRequest = run {
                    // media type and body content are null when a request has no body content
                    // (e.g., a GET request with query params)
                    val body = when {
                        (bodyContent != null) && (mediaType != null) -> bodyContent.asRequestBody(mediaType)
                        else -> null
                    } ?: TracyHttpRequestBody.Empty

                    body.asRequestView(
                        contentType = mediaType?.toContentType(),
                        url = request.url.toProtocolUrl(),
                        method = request.method,
                    )
                }

                adapter.registerRequest(span, tracyRequest)

                // register response
                val response = chain.proceed(request)
                adapter.registerResponse(span, response = response.asResponseView())

                // response is of streaming type when its body MIME type is `text/event-stream`
                isStreamingResponse = response.body.contentType()?.let {
                    "${it.type}/${it.subtype}" == "text/event-stream"
                } ?: false

                // wrap trace SSE events into span when the response body is of the ` text / event-stream ` content type
                return when {
                    isStreamingResponse -> response.withTracedSSE(span, requestUrl = request.url.toProtocolUrl())
                    else -> response
                }
            } catch (e: Exception) {
                span.setStatus(StatusCode.ERROR)
                span.recordException(e)
                throw e
            } finally {
                if (!isStreamingResponse) {
                    span.end()
                }
            }
        }
    }

    private fun OkHttpResponse.withTracedSSE(span: Span, requestUrl: TracyHttpUrl): OkHttpResponse {
        val originalResponse = this
        val originalBody = originalResponse.body ?: return originalResponse

        val originalBodyWithTracedSSE = object : OkHttpResponseBody() {
            override fun contentType() = originalBody.contentType()
            override fun contentLength() = originalBody.contentLength()

            override fun source(): BufferedSource {
                // capture SSE events and forward them into the adapter
                val forwardingSource = SseCapturingSource(
                    delegate = originalBody.source(),
                    adapter, span, requestUrl,
                )
                return forwardingSource.buffer()
            }

            override fun close() {
                span.end()
            }
        }

        return originalResponse.newBuilder()
            .body(originalBodyWithTracedSSE)
            .build()
    }

    private fun OkHttpRequest.withCopiedBodyContent(): Pair<ByteArray?, OkHttpRequest> {
        val body = this.body ?: return null to this
        val mediaType = body.contentType()

        // read body content
        val content = Buffer().let {
            body.writeTo(it)
            it.readByteArray()
        }

        val request = if (body.isOneShot()) {
            val newBody = content.toRequestBody(mediaType)
            this.newBuilder()
                .method(this.method, newBody)
                .build()
        } else {
            // if the body can be read multiple times,
            // then we can reuse the same request
            this
        }

        return content to request
    }

    private fun OkHttpResponse.asResponseView(): TracyHttpResponse {
        val response = this
        val mediaType = response.body.contentType()
        val mimeType = mediaType?.let { "${it.type}/${it.subtype}" }

        // select a body type based on the response MIME type
        val body = when(mimeType) {
            "application/json" -> try {
                val json = Json.decodeFromString<JsonObject>(response.peekBody(Long.MAX_VALUE).string())
                TracyHttpResponseBody.Json(json)
            } catch (_: Exception) {
                TracyHttpResponseBody.Empty
            }
            "text/event-stream" -> TracyHttpResponseBody.EventStream
            else -> TracyHttpResponseBody.Empty
        }

        return object : TracyHttpResponse {
            override val contentType = mediaType?.toContentType()
            override val code = response.code
            override val body = body
            override val url = response.request.url.toProtocolUrl()
            override val requestMethod = response.request.method.uppercase()

            override fun isError() = response.isSuccessful.not()
        }
    }

    private fun ByteArray.asRequestBody(mediaType: MediaType) = this.asRequestBody(
        contentType = mediaType.toContentType(),
        charset = mediaType.charset() ?: Charsets.UTF_8,
    )
}
