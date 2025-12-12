package ai.dev.kit.clients

import ai.dev.kit.OpenTelemetryOkHttpInterceptor
import ai.dev.kit.adapters.AnthropicLLMTracingAdapter
import ai.dev.kit.patchOpenAICompatibleClient
import com.anthropic.client.AnthropicClient

fun instrument(client: AnthropicClient): AnthropicClient {
    return patchOpenAICompatibleClient(
        client = client,
        interceptor = OpenTelemetryOkHttpInterceptor(adapter = AnthropicLLMTracingAdapter())
    )
}
