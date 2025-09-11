package ai.dev.kit.adapters

import ai.dev.kit.adapters.openai.ChatCompletionsHandler
import ai.dev.kit.adapters.openai.OpenAIApiHandler
import ai.dev.kit.adapters.openai.OpenAIApiUtils
import ai.dev.kit.adapters.openai.ResponsesApiHandler
import io.opentelemetry.api.trace.Span
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_SYSTEM
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
    private var handler: OpenAIApiHandler? = null

    override fun getRequestBodyAttributes(span: Span, url: Url, body: JsonObject) {
        // Set a common system attribute
        span.setAttribute(GEN_AI_SYSTEM, GenAiSystemIncubatingValues.OPENAI)
        if (handler == null) {
            handler = when (detectApiType(body)) {
                OpenAIApiType.CHAT_COMPLETIONS -> ChatCompletionsHandler()
                OpenAIApiType.RESPONSES_API -> ResponsesApiHandler()
            }
        }

        handler?.handleRequestAttributes(span, url, body)
    }

    override fun getResultBodyAttributes(span: Span, body: JsonObject) {
        OpenAIApiUtils.setCommonResponseAttributes(span, body)

        handler?.handleResponseAttributes(span, body)
    }

    override fun handleStreaming(span: Span, events: String) {
        handler?.handleStreaming(span, events)
    }
}

private fun detectApiType(
    requestBody: JsonObject?
): OpenAIApiType {
    requestBody?.let { body ->
        if (body.containsKey("messages")) return OpenAIApiType.CHAT_COMPLETIONS
        if (body.containsKey("input")) return OpenAIApiType.RESPONSES_API
    }

    return OpenAIApiType.CHAT_COMPLETIONS
}
