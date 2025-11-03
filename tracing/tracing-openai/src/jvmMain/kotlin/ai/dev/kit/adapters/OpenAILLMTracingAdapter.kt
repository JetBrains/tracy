package ai.dev.kit.adapters

import ai.dev.kit.adapters.openai.ChatCompletionsHandler
import ai.dev.kit.adapters.openai.ImagesGenerationsHandler
import ai.dev.kit.adapters.openai.OpenAIApiHandler
import ai.dev.kit.adapters.openai.OpenAIApiUtils
import ai.dev.kit.adapters.openai.ResponsesApiHandler
import ai.dev.kit.adapters.openai.media.ChatCompletionsMediaContentExtractor
import ai.dev.kit.adapters.openai.media.ResponsesMediaContentExtractor
import io.opentelemetry.api.trace.Span
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GenAiSystemIncubatingValues
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive


/**
 * Detects which OpenAI API is being used based on the request / response structure
 */
private enum class OpenAIApiType(val route: String) {
    // See: https://platform.openai.com/docs/api-reference/completions
    CHAT_COMPLETIONS("completions"),
    // See: https://platform.openai.com/docs/api-reference/responses
    RESPONSES_API("responses"),
    // See: https://platform.openai.com/docs/api-reference/images/create
    IMAGES_GENERATIONS("images/generations"),
}

class OpenAILLMTracingAdapter : LLMTracingAdapter(genAISystem = GenAiSystemIncubatingValues.OPENAI) {
    private var handler: OpenAIApiHandler? = null

    override fun getRequestBodyAttributes(span: Span, url: Url, body: JsonObject) {
        if (handler == null) {
            handler = when (detectApiType(url)) {
                OpenAIApiType.CHAT_COMPLETIONS -> ChatCompletionsHandler(
                    extractor = ChatCompletionsMediaContentExtractor()
                )
                OpenAIApiType.RESPONSES_API -> ResponsesApiHandler(
                    extractor = ResponsesMediaContentExtractor()
                )
                OpenAIApiType.IMAGES_GENERATIONS -> ImagesGenerationsHandler()
            }
        }
        handler?.handleRequestAttributes(span, url, body)
    }

    override fun getResultBodyAttributes(span: Span, body: JsonObject) {
        OpenAIApiUtils.setCommonResponseAttributes(span, body)

        handler?.handleResponseAttributes(span, body)
    }

    override fun isStreamingRequest(body: JsonObject?) =
        body?.get("stream")?.jsonPrimitive?.boolean == true

    override fun handleStreaming(span: Span, events: String) {
        handler?.handleStreaming(span, events)
    }
}

private fun detectApiType(url: Url): OpenAIApiType {
    val route = url.pathSegments.joinToString(separator = "/")
    return when {
        route.endsWith(OpenAIApiType.CHAT_COMPLETIONS.route) -> OpenAIApiType.CHAT_COMPLETIONS
        route.endsWith(OpenAIApiType.RESPONSES_API.route) -> OpenAIApiType.RESPONSES_API
        route.endsWith(OpenAIApiType.IMAGES_GENERATIONS.route) -> OpenAIApiType.IMAGES_GENERATIONS
        else -> throw IllegalArgumentException("Unknown API type with route '$route'")
    }
}
