package ai.dev.kit.clients

import ai.dev.kit.adapters.GeminiLLMTracingAdapter
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import com.google.genai.Client as GeminiClient


fun instrument(client: GeminiClient): GeminiClient {
    return patchClient(client, interceptor = OpenTelemetryGeminiLogger())
}

private fun patchClient(client: GeminiClient, interceptor: Interceptor): GeminiClient {
    val apiClientField = GeminiClient::class.java.getDeclaredField("apiClient")
        .apply { isAccessible = true }
    val apiClient = apiClientField.get(client)

    val httpClientField = apiClient.javaClass.superclass.getDeclaredField("httpClient")
        .apply { isAccessible = true }
    val httpClient = httpClientField.get(apiClient)

    val interceptorsField = OkHttpClient::class.java.getDeclaredField("interceptors").apply { isAccessible = true }
    interceptorsField.set(httpClient, listOf(interceptor))

    return client
}

/**
 * For request and response schemas, see: [Gemini Docs](https://ai.google.dev/api/generate-content)
 */
class OpenTelemetryGeminiLogger :
    OpenTelemetryOkHttpInterceptor("Gemini-generation", adapter = GeminiLLMTracingAdapter())
