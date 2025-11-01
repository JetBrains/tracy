package ai.dev.kit

import ai.dev.kit.adapters.ContentType
import ai.dev.kit.adapters.LLMTracingAdapter
import ai.dev.kit.adapters.Url
import ai.dev.kit.tracing.TracingManager
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer

fun instrument(client: OkHttpClient, interceptor: OpenTelemetryOkHttpInterceptor): OkHttpClient {
    val clientBuilder = client.newBuilder()
    patchInterceptorsInplace(clientBuilder.interceptors(), interceptor)
    return clientBuilder.build()
}

/**
 * Patches the OpenAI-compatible client by injecting a custom interceptor into its internal HTTP client.
 *
 * This method modifies the internal structure of the provided OpenAI-like client to replace its HTTP client interceptors
 * with the specified interceptor.
 * Supports OpenAI-compatible (**in terms of internal class structure**) clients.
 *
 * @param client The instance of the OpenAI-compatible client to patch.
 * @param interceptor The interceptor to be injected into the internal HTTP client of the OpenAI-compatible client.
 * @return The patched client instance with the custom interceptor injected into its HTTP client.
 */
fun <T> patchOpenAICompatibleClient(
    client: T,
    interceptor: Interceptor,
): T {
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

    return client
}

fun getFieldValue(instance: Any, fieldName: String): Any {
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

fun setFieldValue(instance: Any, fieldName: String, value: Any?) {
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


abstract class OpenTelemetryOkHttpInterceptor(
    private val spanName: String,
    private val adapter: LLMTracingAdapter,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val tracer = TracingManager.tracer

        val span = tracer.spanBuilder(spanName).startSpan()
        var isStreamingRequest = false

        span.makeCurrent().use { _ ->
            try {
                val request = chain.request()
                val url = request.url
                val body = request.body?.let {
                    val buffer = Buffer()
                    it.writeTo(buffer)
                    Json.parseToJsonElement(buffer.readUtf8()).jsonObject
                }
                isStreamingRequest = adapter.isStreamingRequest(body)

                adapter.registerRequest(
                    span = span,
                    url = Url(url.scheme, url.host, url.pathSegments),
                    requestBody = body ?: JsonObject(emptyMap()),
                )


                val response = chain.proceed(chain.request())
                val contentType = response.body?.contentType()

                if (isStreamingRequest) {
                    val streamingMarker = JsonObject(mapOf("stream" to JsonPrimitive(true)))

                    adapter.registerResponse(
                        span = span,
                        contentType = contentType?.let { ContentType(contentType.type, contentType.subtype) },
                        responseCode = response.code.toLong(),
                        responseBody = streamingMarker,
                    )

                    return wrapStreamingResponse(response, span)
                }

                val decodedResponse = try {
                    Json.decodeFromString<JsonObject>(response.peekBody(Long.MAX_VALUE).string())
                } catch (_: Exception) {
                    JsonObject(emptyMap())
                }

                adapter.registerResponse(
                    span = span,
                    contentType = contentType?.let { ContentType(contentType.type, contentType.subtype) },
                    response.code.toLong(),
                    decodedResponse
                )

                return response
            } catch (e: Exception) {
                span.setStatus(StatusCode.ERROR)
                span.recordException(e)
                throw e
            } finally {
                if (!isStreamingRequest) span.end()
            }
        }
    }

    internal fun wrapStreamingResponse(originalResponse: Response, span: Span): Response {
        val originalBody = originalResponse.body ?: return originalResponse

        val tracingBody = object : ResponseBody() {
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
                    adapter.handleStreaming(span, capturedText.toString())
                } finally {
                    span.end()
                }
            }
        }

        return originalResponse.newBuilder().body(tracingBody).build()
    }
}