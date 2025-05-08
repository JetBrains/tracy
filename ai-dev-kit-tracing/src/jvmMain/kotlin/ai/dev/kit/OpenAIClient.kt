package ai.dev.kit

import ai.dev.kit.tracing.fluent.KotlinFlowTrace
import ai.dev.kit.tracing.fluent.SpanType
import ai.dev.kit.tracing.fluent.handlers.OpenAiClientAttributeHandler
import ai.dev.kit.tracing.fluent.processor.withTrace
import com.openai.client.OpenAIClient
import com.openai.client.OpenAIClientImpl
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.core.ClientOptions
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response

fun createOpenAIClient(): OpenAIClient {
    val openAIClient = OpenAIOkHttpClient.builder()
        .fromEnv()
        .build().apply {
            patchClient(this, interceptor = MLFlowOpenAILogger())
        }

    return openAIClient
}

private fun patchClient(openAIClient: OpenAIClient, interceptor: Interceptor) {
    val clientOptionsField =
        OpenAIClientImpl::class.java.getDeclaredField("clientOptions").apply { isAccessible = true }
    val clientOptions = clientOptionsField.get(openAIClient)

    val originalHttpClientField =
        ClientOptions::class.java.getDeclaredField("originalHttpClient").apply { isAccessible = true }
    val originalHttpClient = originalHttpClientField.get(clientOptions)

    val okHttpClientField =
        com.openai.client.okhttp.OkHttpClient::class.java.getDeclaredField("okHttpClient").apply { isAccessible = true }
    val okHttpClient = okHttpClientField.get(originalHttpClient) as OkHttpClient

    val interceptorsField = OkHttpClient::class.java.getDeclaredField("interceptors").apply { isAccessible = true }

    interceptorsField.set(okHttpClient, listOf(interceptor))
}

class MLFlowOpenAILogger : Interceptor {
    @KotlinFlowTrace(
        name = "Completions",
        spanType = SpanType.CHAT_MODEL,
        attributeHandler = OpenAiClientAttributeHandler::class
    )
    override fun intercept(chain: Interceptor.Chain): Response = withTrace(
        function = ::intercept,
        args = arrayOf<Any?>(chain),
    ) {
        return@withTrace chain.proceed(chain.request())
    }
}
