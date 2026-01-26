package ai.jetbrains.tracy.openai.clients

import ai.jetbrains.tracy.okhttp.interceptors.OpenTelemetryOkHttpInterceptor
import ai.jetbrains.tracy.okhttp.interceptors.patchOpenAICompatibleClient
import ai.jetbrains.tracy.openai.adapters.OpenAILLMTracingAdapter
import com.openai.client.OpenAIClient

/**
 * Instruments OpenAI client with OpenTelemetry instrumentation.
 *
 * All calls using this client will be traced.
 *
 * @param client OpenAI client to instrument
 * @return the original client with instrumentation
 */
fun instrument(client: OpenAIClient): OpenAIClient {
    return patchOpenAICompatibleClient(
        client = client,
        interceptor = OpenTelemetryOkHttpInterceptor(adapter = OpenAILLMTracingAdapter())
    )
}
