package ai.jetbrains.tracy.okhttp.interceptors

import ai.jetbrains.tracy.core.adapters.LLMTracingAdapter
import ai.jetbrains.tracy.core.http.protocol.*
import ai.jetbrains.tracy.core.tracing.TracingManager
import ai.jetbrains.tracy.okhttp.extensions.toProtocolUrl
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import mu.KotlinLogging
import okhttp3.Interceptor
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import okhttp3.Request as OkHttpRequest
import okhttp3.Response as OkHttpResponse
import okhttp3.ResponseBody as OkHttpResponseBody

/**
 * Intercepts OkHttp calls and traces them using the provided [adapter].
 */
class OpenTelemetryOkHttpInterceptor(
    private val adapter: LLMTracingAdapter,
) : Interceptor {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun intercept(chain: Interceptor.Chain): OkHttpResponse {
        if (!TracingManager.isTracingEnabled) {
            return chain.proceed(chain.request())
        }

        val tracer = TracingManager.tracer

        val span = tracer.spanBuilder("").startSpan()
        var isStreamingRequest = false

        span.makeCurrent().use { _ ->
            try {
                // register request
                val (bodyContent, request) = chain.request().withCopiedBodyContent()

                if (bodyContent != null) {
                    val mediaType = request.body?.contentType()
                    val req = bodyContent.asRequestBody(mediaType)?.let {
                        Request(
                            url = request.url.toProtocolUrl(),
                            contentType = mediaType?.toContentType(),
                            body = it,
                        )
                    }
                    if (req != null) {
                        isStreamingRequest = adapter.isStreamingRequest(req)
                        adapter.registerRequest(span, req)
                    } else {
                        logger.warn { "Failed to register request, cannot build request from body content with media type of $mediaType" }
                    }
                } else {
                    logger.warn { "Failed to register request, body content is null" }
                }

                // register response
                val response = chain.proceed(request)
                val responseMediaType = response.body?.contentType()

                return if (isStreamingRequest) {
                    val streamingMarker = JsonObject(mapOf("stream" to JsonPrimitive(true)))
                    val url = request.url.toProtocolUrl()
                    adapter.registerResponse(
                        span = span,
                        response = Response(
                            contentType = responseMediaType?.toContentType(),
                            code = response.code,
                            body = ResponseBody.Json(streamingMarker),
                            url = url,
                        ),
                    )
                    wrapStreamingResponse(response, url, span)
                } else {
                    val decodedResponse = try {
                        Json.decodeFromString<JsonObject>(response.peekBody(Long.MAX_VALUE).string())
                    } catch (_: Exception) {
                        JsonObject(emptyMap())
                    }
                    adapter.registerResponse(
                        span = span,
                        response = Response(
                            contentType = responseMediaType?.toContentType(),
                            code = response.code,
                            body = ResponseBody.Json(decodedResponse),
                            url = request.url.toProtocolUrl(),
                        ),
                    )
                    response
                }
            } catch (e: Exception) {
                span.setStatus(StatusCode.ERROR)
                span.recordException(e)
                throw e
            } finally {
                if (!isStreamingRequest) {
                    span.end()
                }
            }
        }
    }

    private fun wrapStreamingResponse(
        originalResponse: OkHttpResponse,
        url: Url,
        span: Span,
    ): OkHttpResponse {
        val originalBody = originalResponse.body ?: return originalResponse

        val tracingBody = object : OkHttpResponseBody() {
            private val capturedText = StringBuilder()

            override fun contentType() = originalBody.contentType()
            override fun contentLength() = -1L

            override fun source(): BufferedSource {
                val originalSource = originalBody.source()

                return object : ForwardingSource(originalSource) {
                    private val acc = Buffer()
                    override fun read(sink: Buffer, byteCount: Long): Long {
                        val bytesRead = try {
                            super.read(sink, byteCount)
                        } catch (e: Exception) {
                            span.setStatus(StatusCode.ERROR)
                            span.recordException(e)
                            span.end()
                            throw e
                        }

                        if (bytesRead > 0) {
                            val start = sink.size - bytesRead
                            sink.copyTo(acc, start, bytesRead)

                            capturedText.append(acc.readUtf8(bytesRead))
                        }

                        return bytesRead
                    }
                }.buffer()
            }

            override fun close() {
                try {
                    adapter.handleStreaming(span, url, capturedText.toString())
                } finally {
                    span.end()
                }
            }
        }

        return originalResponse.newBuilder().body(tracingBody).build()
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
}
