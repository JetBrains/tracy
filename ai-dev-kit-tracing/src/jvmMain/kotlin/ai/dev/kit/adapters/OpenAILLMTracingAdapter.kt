package ai.dev.kit.adapters

import ai.dev.kit.adapters.openai.ChatCompletionsHandler
import ai.dev.kit.adapters.openai.ResponsesApiHandler
import io.opentelemetry.api.trace.Span
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GenAiSystemIncubatingValues
import kotlinx.serialization.json.JsonObject


/**
 * Detects which OpenAI API is being used based on the request / response structure
 */
private enum class OpenAIApiType {
    // See: https://platform.openai.com/docs/api-reference/completions
    CHAT_COMPLETIONS,
    // See: https://platform.openai.com/docs/api-reference/responses
    RESPONSES_API,
}

internal class OpenAILLMTracingAdapter : LLMTracingAdapter(genAISystem = GenAiSystemIncubatingValues.OPENAI) {
    private val chatHandler = ChatCompletionsHandler()
    private val responsesHandler = ResponsesApiHandler()

    override fun getRequestBodyAttributes(span: Span, url: Url, body: JsonObject) {
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
