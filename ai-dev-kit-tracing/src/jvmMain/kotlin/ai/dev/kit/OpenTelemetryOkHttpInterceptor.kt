package ai.dev.kit

import ai.dev.kit.tracing.AI_DEVELOPMENT_KIT_TRACER
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_SYSTEM
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
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
internal fun <Client, ClientImpl, ClientOptions, ClientOkHttpClient> patchOpenAICompatibleClient(
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

abstract class OpenTelemetryOkHttpInterceptor(private val spanName: String, private val genAISystem: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val tracer = GlobalOpenTelemetry.getTracer(AI_DEVELOPMENT_KIT_TRACER)

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

                body?.let { getRequestBodyAttributes(span, url, it) }
                span.setAttribute("gen_ai.api_base", "${url.scheme}://${url.host}")

                // TODO: get from parameters
                span.setAttribute(GEN_AI_SYSTEM, genAISystem)

                val response = chain.proceed(chain.request())
                val contentType = response.body?.contentType()
                val requiredContentType = "application/json".toMediaType()

                if (contentType?.type == requiredContentType.type &&
                    contentType.subtype == requiredContentType.subtype) {
                    // We need to peek the body so the stream is not consumed
                    val decodedResponse = Json.decodeFromString<JsonObject>(response.peekBody(Long.MAX_VALUE).string())
                    getResultBodyAttributes(span, decodedResponse)
                } else {
                    contentType?.let { span.setAttribute("gen_ai.completion.content.type", it.toString()) }
                }

                span.setAttribute("http.status_code", response.code.toLong())
                if (response.code in 400..499 || response.code in 500..599) {
                    val decodedResponse = Json.decodeFromString<JsonObject>(response.peekBody(Long.MAX_VALUE).string())
                    getResultErrorBodyAttributes(span, decodedResponse)
                    span.setStatus(StatusCode.ERROR)
                }
                else {
                    span.setStatus(StatusCode.OK)
                }
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

    protected open fun getResultErrorBodyAttributes(span: Span, body: JsonObject) {
        body["error"]?.jsonObject?.let {
            it["message"]?.jsonPrimitive?.let { span.setAttribute("gen_ai.error.message", it.content) }
            it["type"]?.jsonPrimitive?.let { span.setAttribute("gen_ai.error.type", it.content) }
            it["param"]?.jsonPrimitive?.let { span.setAttribute("gen_ai.error.param", it.content) }
            it["code"]?.jsonPrimitive?.let { span.setAttribute("gen_ai.error.code", it.content) }
        }
    }

    protected abstract fun getRequestBodyAttributes(span: Span, url: HttpUrl, body: JsonObject)

    protected abstract fun getResultBodyAttributes(span: Span, body: JsonObject)
}