package ai.dev.kit.clients

import ai.dev.kit.OpenTelemetryOkHttpInterceptor
import ai.dev.kit.adapters.AnthropicLLMTracingAdapter
import ai.dev.kit.patchOpenAICompatibleClient
import com.anthropic.client.AnthropicClient

fun instrument(client: AnthropicClient): AnthropicClient {
    return patchOpenAICompatibleClient(
        client = client,
        interceptor = OpenTelemetryAnthropicLogger()
    )
}

/**
 * For request and response schemas, see: [Docs](https://docs.anthropic.com/en/api/messages)
 *
 * For API errors, see: [Docs](https://docs.anthropic.com/en/api/errors)
 */
class OpenTelemetryAnthropicLogger :
    OpenTelemetryOkHttpInterceptor("Anthropic-generation", adapter = AnthropicLLMTracingAdapter())
