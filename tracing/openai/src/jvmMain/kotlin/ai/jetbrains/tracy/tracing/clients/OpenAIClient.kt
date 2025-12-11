package ai.jetbrains.tracy.tracing.clients

import ai.dev.kit.OpenTelemetryOkHttpInterceptor
import ai.jetbrains.tracy.tracing.adapters.OpenAILLMTracingAdapter
import ai.dev.kit.patchOpenAICompatibleClient
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
        interceptor = OpenAIOpenTelemetryOkHttpInterceptor()
    )
}

/**
 * TODO: remove from the public API
 */
class OpenAIOpenTelemetryOkHttpInterceptor :
    OpenTelemetryOkHttpInterceptor("OpenAI-generation", adapter = OpenAILLMTracingAdapter())
