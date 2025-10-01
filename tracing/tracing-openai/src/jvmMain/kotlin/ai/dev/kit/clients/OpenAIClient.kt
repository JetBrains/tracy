package ai.dev.kit.clients

import ai.dev.kit.OpenTelemetryOkHttpInterceptor
import ai.dev.kit.adapters.OpenAILLMTracingAdapter
import ai.dev.kit.patchOpenAICompatibleClient
import com.openai.client.OpenAIClient
import com.openai.client.OpenAIClientImpl
import com.openai.client.okhttp.OkHttpClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.core.ClientOptions
import okhttp3.Interceptor

@Deprecated("instrument() instead")
fun createOpenAIClient(): OpenAIClient {
    val openAIClient = OpenAIOkHttpClient.builder()
        .fromEnv()
        .build().apply {
            patchClient(this, interceptor = OpenTelemetryOpenAILogger())
        }

    return openAIClient
}

fun instrument(client: OpenAIClient): OpenAIClient {
    return patchClient(client, interceptor = OpenTelemetryOpenAILogger())
}

private fun patchClient(openAIClient: OpenAIClient, interceptor: Interceptor): OpenAIClient {
    return patchOpenAICompatibleClient(
        client = openAIClient,
        clientImplClass = OpenAIClientImpl::class.java,
        clientOptionsClass = ClientOptions::class.java,
        clientOkHttpClientClass = OkHttpClient::class.java,
        interceptor = interceptor,
    )
}

class OpenTelemetryOpenAILogger :
    OpenTelemetryOkHttpInterceptor("OpenAI-generation", adapter = OpenAILLMTracingAdapter())
