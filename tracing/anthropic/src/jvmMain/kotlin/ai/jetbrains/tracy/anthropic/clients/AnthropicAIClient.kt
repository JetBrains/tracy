package ai.jetbrains.tracy.anthropic.clients

import ai.jetbrains.tracy.anthropic.adapters.AnthropicLLMTracingAdapter
import ai.jetbrains.tracy.okhttp.interceptors.OpenTelemetryOkHttpInterceptor
import ai.jetbrains.tracy.okhttp.interceptors.patchOpenAICompatibleClient
import com.anthropic.client.AnthropicClient

fun instrument(client: AnthropicClient): AnthropicClient {
    return patchOpenAICompatibleClient(
        client = client,
        interceptor = OpenTelemetryOkHttpInterceptor(adapter = AnthropicLLMTracingAdapter())
    )
}
