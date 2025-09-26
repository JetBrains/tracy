package ai.dev.kit

import ai.dev.kit.adapters.ContentType
import ai.dev.kit.adapters.LLMTracingAdapter
import ai.dev.kit.adapters.Url
import ai.dev.kit.tracing.TracingManager
import io.opentelemetry.api.trace.StatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response


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

        span.makeCurrent().use { scopeIgnored ->
            try {
                val request = chain.request()
                val url = request.url
                val body = request.body?.let {
                    val buffer = okio.Buffer()
                    it.writeTo(buffer)
                    Json.parseToJsonElement(buffer.readUtf8()).jsonObject
                }

                adapter.registerRequest(
                    span = span,
                    url = Url(url.scheme, url.host, url.pathSegments),
                    requestBody = body ?: JsonObject(emptyMap()),
                )


                val response = chain.proceed(chain.request())
                val contentType = response.body?.contentType()
                val decodedResponse = try {
                    Json.decodeFromString<JsonObject>(response.peekBody(Long.MAX_VALUE).string())
                }
                catch(_: Exception) {
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
                span.end()
            }
        }
    }
}