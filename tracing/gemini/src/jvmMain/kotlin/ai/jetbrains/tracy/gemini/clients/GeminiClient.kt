/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package ai.jetbrains.tracy.gemini.clients

import ai.jetbrains.tracy.core.OpenTelemetryOkHttpInterceptor
import ai.jetbrains.tracy.core.patchInterceptors
import ai.jetbrains.tracy.gemini.adapters.GeminiLLMTracingAdapter
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import com.google.genai.Client as GeminiClient


/**
 * Instruments a Google Gemini client with OpenTelemetry tracing.
 *
 * All LLM API calls made using this client will be automatically traced,
 * capturing request/response attributes as span data.
 *
 * @param client The [GeminiClient] instance to instrument.
 * @return The same client instance with tracing instrumentation applied.
 *
 * @see GeminiLLMTracingAdapter
 */
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
            "Unsupported Gemini client version. Instrumentation is supported for java-genai version 1.8.0 or higher.", e
        )
    }

    return client
}
