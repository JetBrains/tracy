package ai.dev.kit.clients

import ai.dev.kit.OpenTelemetryOkHttpInterceptor
import ai.dev.kit.adapters.OpenAILLMTracingAdapter
import ai.dev.kit.patchOpenAICompatibleClient
import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient

@Deprecated("instrument() instead")
fun createOpenAIClient(): OpenAIClient {
    val openAIClient = OpenAIOkHttpClient.builder()
        .fromEnv()
        .build().apply {
            patchOpenAICompatibleClient(
                client = this,
                interceptor = OpenTelemetryOpenAILogger()
            )
        }

    return openAIClient
}

fun instrument(client: OpenAIClient): OpenAIClient {
    return patchOpenAICompatibleClient(
        client = client,
        interceptor = OpenTelemetryOpenAILogger()
    )
}

class OpenTelemetryOpenAILogger :
    OpenTelemetryOkHttpInterceptor("OpenAI-generation", adapter = OpenAILLMTracingAdapter())
