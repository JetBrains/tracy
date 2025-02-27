package org.example.ai

import com.openai.client.OpenAIClient
import com.openai.client.OpenAIClientImpl
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.core.ClientOptions
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.example.ai.mlflow.fluent.KotlinFlowTrace
import org.example.ai.mlflow.fluent.SpanType
import org.example.ai.mlflow.fluent.processor.SpanAttributeHandlerType

fun createOpenAIClient(): OpenAIClient {
    val openAIClient = OpenAIOkHttpClient.builder()
        .fromEnv()
        .build().apply { patchClient(this, interceptor = MLFlowOpenAILogger()) }

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
    @KotlinFlowTrace(name="Completions", spanType = SpanType.CHAT_MODEL, attributeHandler = SpanAttributeHandlerType.OPEN_AI_CLIENT)
    override fun intercept(chain: Interceptor.Chain): Response {
        return chain.proceed(chain.request())
    }
}
