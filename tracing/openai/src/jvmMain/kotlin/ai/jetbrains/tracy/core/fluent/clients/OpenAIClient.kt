package ai.jetbrains.tracy.core.fluent.clients

import ai.jetbrains.tracy.core.OpenTelemetryOkHttpInterceptor
import ai.jetbrains.tracy.core.fluent.adapters.OpenAILLMTracingAdapter
import ai.jetbrains.tracy.core.patchOpenAICompatibleClient
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
