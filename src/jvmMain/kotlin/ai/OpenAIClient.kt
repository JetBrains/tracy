package org.example.ai

import ai.core.fluent.KotlinFlowTrace
import ai.core.fluent.SpanType
import org.example.ai.core.fluent.handlers.OpenAiClientAttributeHandler
import ai.core.fluent.processor.withTrace
import com.openai.client.OpenAIClient
import com.openai.client.OpenAIClientImpl
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.core.ClientOptions
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import ai.mlflow.fluent.MlflowTracingMetadataConfigurator

fun createOpenAIClient(dumbTraceMode: Boolean = false): OpenAIClient {
    val openAIClient = OpenAIOkHttpClient.builder()
        .fromEnv()
        .build().apply {
            patchClient(this, interceptor = if (dumbTraceMode) MLFlowDumbOpenAILogger() else MLFlowOpenAILogger())
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
    @KotlinFlowTrace(name="Completions", spanType = SpanType.CHAT_MODEL, attributeHandler = OpenAiClientAttributeHandler::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        return chain.proceed(chain.request())
    }
}

class MLFlowDumbOpenAILogger : Interceptor {
    @KotlinFlowTrace(name="Completions", spanType = SpanType.CHAT_MODEL, attributeHandler = OpenAiClientAttributeHandler::class)
    override fun intercept(chain: Interceptor.Chain): Response = withTrace(
        function = ::intercept,
        args = arrayOf<Any?>(chain),
        tracingMetadataConfigurator = MlflowTracingMetadataConfigurator
    )
    {
        return@withTrace chain.proceed(chain.request())
    }
}
