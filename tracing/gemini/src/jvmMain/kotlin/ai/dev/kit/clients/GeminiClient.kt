package ai.dev.kit.clients

import ai.dev.kit.OpenTelemetryOkHttpInterceptor
import ai.dev.kit.adapters.GeminiLLMTracingAdapter
import ai.dev.kit.patchInterceptors
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import com.google.genai.Client as GeminiClient


fun instrument(client: GeminiClient): GeminiClient {
    return patchClient(
        client,
        interceptor = OpenTelemetryOkHttpInterceptor(adapter = GeminiLLMTracingAdapter())
    )
}

private fun patchClient(client: GeminiClient, interceptor: Interceptor): GeminiClient {
    val apiClientField = GeminiClient::class.java.getDeclaredField("apiClient")
        .apply { isAccessible = true }
    val apiClient = apiClientField.get(client)

    val httpClientField = apiClient.javaClass.superclass.getDeclaredField("httpClient")
        .apply { isAccessible = true }
    val httpClient = httpClientField.get(apiClient) as OkHttpClient

    // install tracing interceptor if not installed already
    val updatedInterceptors = patchInterceptors(httpClient.interceptors, interceptor)
    val interceptorsField = OkHttpClient::class.java.getDeclaredField("interceptors").apply { isAccessible = true }

    try {
        interceptorsField.set(httpClient, updatedInterceptors)
    } catch (e: IllegalArgumentException) {
        throw IllegalStateException(
            "Unsupported Gemini client version. Instrumentation is supported for java-genai version 1.8.0 or higher.", e)
    }

    return client
}
