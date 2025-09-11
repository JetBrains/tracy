package ai.dev.kit

import ai.dev.kit.adapters.ContentType
import ai.dev.kit.adapters.LLMTracingAdapter
import ai.dev.kit.adapters.Url
import ai.dev.kit.tracing.TracingManager
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import kotlinx.serialization.json.*
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer


/**
 * Patches the OpenAI-compatible client by injecting a custom interceptor into its internal HTTP client.
 *
 * This method modifies the internal structure of the provided OpenAI-like client to replace its HTTP client interceptors
 * with the specified interceptor.
 * Supports OpenAI-compatible (**in terms of internal class structure**) clients.
 *
 * @param Client The generic type representing the client class (e.g., Anthropic or OpenAI client interfaces).
 * @param ClientImpl The generic type representing the implementation class of the client.
 * @param ClientOptions The generic type representing the options class of the client.
 * @param ClientOkHttpClient The generic type representing the OkHttp client class used internally by the client.
 * @param client The instance of the OpenAI-compatible client to patch.
 * @param clientImplClass The class object representing the implementation type of the client.
 * @param clientOptionsClass The class object representing the options type of the client.
 * @param clientOkHttpClientClass The class object representing the OkHttp client type used internally by the client.
 * @param interceptor The interceptor to be injected into the internal HTTP client of the OpenAI-compatible client.
 * @return The patched client instance with the custom interceptor injected into its HTTP client.
 */
fun <Client, ClientImpl, ClientOptions, ClientOkHttpClient> patchOpenAICompatibleClient(
    client: Client,
    clientImplClass: Class<out ClientImpl>,
    clientOptionsClass: Class<out ClientOptions>,
    clientOkHttpClientClass: Class<out ClientOkHttpClient>,
    interceptor: Interceptor,
): Client {
    val clientOptionsField = clientImplClass.getDeclaredField("clientOptions").apply { isAccessible = true }
    val clientOptions = clientOptionsField.get(client)

    val originalHttpClientField = clientOptionsClass.getDeclaredField("originalHttpClient").apply { isAccessible = true }
    val originalHttpClient = originalHttpClientField.get(clientOptions)

    val okHttpClientField = clientOkHttpClientClass.getDeclaredField("okHttpClient").apply { isAccessible = true }
    val okHttpClient = okHttpClientField.get(originalHttpClient) as OkHttpClient

    val interceptorsField = OkHttpClient::class.java.getDeclaredField("interceptors").apply { isAccessible = true }

    interceptorsField.set(okHttpClient, listOf(interceptor))

    return client
}


abstract class OpenTelemetryOkHttpInterceptor(
    private val spanName: String,
    private val adapter: LLMTracingAdapter,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val tracer = TracingManager.tracer

        val span = tracer.spanBuilder(spanName).startSpan()
        var isStreamingRequest = false

        span.makeCurrent().use { scopeIgnored ->
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