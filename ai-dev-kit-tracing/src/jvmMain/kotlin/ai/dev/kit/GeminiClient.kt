package ai.dev.kit

import ai.dev.kit.tracing.AI_DEVELOPMENT_KIT_TRACER
import com.google.genai.HttpApiClient
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_SYSTEM
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GenAiSystemIncubatingValues
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import com.google.genai.Client as GeminiClient

fun instrument(client: GeminiClient): GeminiClient {
    return patchClient(client, interceptor = OpenTelemetryGeminiLogger())
}

private fun patchClient(client: GeminiClient, interceptor: Interceptor): GeminiClient {
    val apiClientField = GeminiClient::class.java.getDeclaredField("apiClient")
        .apply { isAccessible = true }
    val apiClient = apiClientField.get(client)

    // TODO: base class is `ApiClient`, see another inheritor `ReplayApiClient`
    val httpClientField = apiClient.javaClass.superclass.getDeclaredField("httpClient")
        .apply { isAccessible = true }
    val httpClient = httpClientField.get(apiClient)

    val interceptorsField = OkHttpClient::class.java.getDeclaredField("interceptors").apply { isAccessible = true }

    interceptorsField.set(httpClient, listOf(interceptor))

    return client
}

private const val SPAN_NAME = "Gemini-generation"

class OpenTelemetryGeminiLogger : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val tracer = GlobalOpenTelemetry.getTracer(AI_DEVELOPMENT_KIT_TRACER)

        val span = tracer.spanBuilder(SPAN_NAME).startSpan()
        val scope = span.makeCurrent()

        try {
            val request = chain.request()
            val url = request.url
            val body = request.body?.let {
                val buffer = okio.Buffer()
                it.writeTo(buffer)
                Json.parseToJsonElement(buffer.readUtf8()).jsonObject
            }

            println("GEMINI REQUEST BODY: $body")
            // TODO: body?.let { getRequestBodyAttributes(span, it) }
            span.setAttribute("gen_ai.gemini.api_base", "${url.scheme}://${url.host}")

            // TODO: get from parameters
            span.setAttribute(GEN_AI_SYSTEM, GenAiSystemIncubatingValues.GEMINI)

            val response = chain.proceed(chain.request())


            val contentType = response.body?.contentType()
            println("GEMINI CONTENT TYPE: $contentType (isJson=${contentType == "application/json".toMediaType()}), ${"application/json".toMediaType()}")

            val requiredMediaType = "application/json".toMediaType()

            if (contentType?.type == requiredMediaType.type &&
                contentType.subtype == requiredMediaType.subtype) {
                // We need to peek the body so the stream is not consumed
                println("GEMINI RESPONSE BODY: ${response.peekBody(Long.MAX_VALUE).string()}")

                val decodedResponse = Json.decodeFromString<JsonObject>(response.peekBody(Long.MAX_VALUE).string())
                // TODO: getResultBodyAttributes(span, decodedResponse)
            } else {
                contentType?.let { span.setAttribute("gen_ai.completion.content.type", it.toString()) }
            }

            return response
        } catch (e: Exception) {
            throw e
        } finally {
            scope.close()
            span.end()
        }
    }
}