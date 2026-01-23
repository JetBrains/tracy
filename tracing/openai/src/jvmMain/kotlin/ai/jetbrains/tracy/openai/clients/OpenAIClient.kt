package ai.jetbrains.tracy.openai.clients

import ai.jetbrains.tracy.core.OpenTelemetryOkHttpInterceptor
import ai.jetbrains.tracy.openai.adapters.OpenAILLMTracingAdapter
import ai.jetbrains.tracy.core.patchOpenAICompatibleClient
import com.openai.client.OpenAIClient

/**
 * Instruments an OpenAI client with OpenTelemetry tracing.
 *
 * All LLM API calls made using this client will be automatically traced,
 * capturing request/response attributes as span data.
 *
 * @param client The [OpenAIClient] instance to instrument.
 * @return The same client instance with tracing instrumentation applied.
 *
 * @see OpenAILLMTracingAdapter
 */
fun instrument(client: OpenAIClient): OpenAIClient {
    return patchOpenAICompatibleClient(
        client = client,
        interceptor = OpenTelemetryOkHttpInterceptor(adapter = OpenAILLMTracingAdapter())
    )
}
