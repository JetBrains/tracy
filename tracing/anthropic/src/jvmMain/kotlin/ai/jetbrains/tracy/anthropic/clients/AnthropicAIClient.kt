package ai.jetbrains.tracy.anthropic.clients

import ai.jetbrains.tracy.core.OpenTelemetryOkHttpInterceptor
import ai.jetbrains.tracy.core.patchOpenAICompatibleClient
import ai.jetbrains.tracy.anthropic.adapters.AnthropicLLMTracingAdapter
import com.anthropic.client.AnthropicClient

fun instrument(client: AnthropicClient): AnthropicClient {
    return patchOpenAICompatibleClient(
        client = client,
        interceptor = OpenTelemetryOkHttpInterceptor(adapter = AnthropicLLMTracingAdapter())
    )
}
