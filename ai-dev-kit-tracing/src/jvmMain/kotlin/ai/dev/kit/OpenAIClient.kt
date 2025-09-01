package ai.dev.kit

import ai.dev.kit.openai.ChatCompletionsHandler
import ai.dev.kit.openai.ResponsesApiHandler
import ai.dev.kit.adapters.OpenAILLMTracingAdapter
import com.openai.client.OpenAIClient
import com.openai.client.OpenAIClientImpl
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.core.ClientOptions
import io.opentelemetry.api.trace.Span
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.*
import kotlinx.serialization.json.*
import okhttp3.HttpUrl
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
        clientOkHttpClientClass = com.openai.client.okhttp.OkHttpClient::class.java,
        interceptor = interceptor,
    )
}

class OpenTelemetryOpenAILogger : OpenTelemetryOkHttpInterceptor("OpenAI-generation", adapter = OpenAILLMTracingAdapter())


/**
 * Detects which OpenAI API is being used based on the request / response structure
 */
private enum class OpenAIApiType {
    CHAT_COMPLETIONS,
    RESPONSES_API
}

private fun detectApiType(requestBody: JsonObject?, responseBody: JsonObject?): OpenAIApiType {
    requestBody?.let { body ->
        if (body.containsKey("messages")) return OpenAIApiType.CHAT_COMPLETIONS
        if (body.containsKey("input")) return OpenAIApiType.RESPONSES_API
    }

    responseBody?.let { body ->
        if (body.containsKey("choices")) return OpenAIApiType.CHAT_COMPLETIONS
        if (body.containsKey("output")) return OpenAIApiType.RESPONSES_API
    }

    // Default to chat completions for backwards compatibility
    return OpenAIApiType.CHAT_COMPLETIONS
}

/*
class OpenTelemetryOpenAILogger : OpenTelemetryOkHttpInterceptor(
    SPAN_NAME,
    genAISystem = GenAiSystemIncubatingValues.OPENAI,
) {
    private val chatHandler = ChatCompletionsHandler()
    private val responsesHandler = ResponsesApiHandler()

    override fun getRequestBodyAttributes(span: Span, url: HttpUrl, body: JsonObject) {
        // Set a common system attribute
        span.setAttribute(GEN_AI_SYSTEM, GenAiSystemIncubatingValues.OPENAI)

        val handler = when (detectApiType(body, null)) {
            OpenAIApiType.CHAT_COMPLETIONS -> chatHandler
            OpenAIApiType.RESPONSES_API -> responsesHandler
        }

        handler.handleRequestAttributes(span, url, body)
    }

    override fun getResultBodyAttributes(span: Span, body: JsonObject) {
        val handler = when (detectApiType(null, body)) {
            OpenAIApiType.CHAT_COMPLETIONS -> chatHandler
            OpenAIApiType.RESPONSES_API -> responsesHandler
        }

        handler.handleResponseAttributes(span, body)
    }
}
*/
