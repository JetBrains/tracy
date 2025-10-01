package ai.dev.kit.clients

import ai.dev.kit.OpenTelemetryOkHttpInterceptor
import ai.dev.kit.patchOpenAICompatibleClient
import com.anthropic.client.AnthropicClient
import com.anthropic.client.AnthropicClientImpl
import com.anthropic.client.okhttp.OkHttpClient
import com.anthropic.core.ClientOptions
import okhttp3.Interceptor

fun instrument(client: AnthropicClient): AnthropicClient {
    return patchClient(
        client,
        interceptor = OpenTelemetryAnthropicLogger()
    )
}

private fun patchClient(client: AnthropicClient, interceptor: Interceptor): AnthropicClient {
    return patchOpenAICompatibleClient(
        client = client,
        clientImplClass = AnthropicClientImpl::class.java,
        clientOptionsClass = ClientOptions::class.java,
        clientOkHttpClientClass = OkHttpClient::class.java,
        interceptor = interceptor,
    )
}

/**
 * For request and response schemas, see: [Docs](https://docs.anthropic.com/en/api/messages)
 *
 * For API errors, see: [Docs](https://docs.anthropic.com/en/api/errors)
 */
private class OpenTelemetryAnthropicLogger :
    OpenTelemetryOkHttpInterceptor("Anthropic-generation", adapter = _root_ide_package_.ai.dev.kit.adapters.AnthropicLLMTracingAdapter())
