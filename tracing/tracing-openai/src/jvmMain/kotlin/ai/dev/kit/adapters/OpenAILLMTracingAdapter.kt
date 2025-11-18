package ai.dev.kit.adapters

import ai.dev.kit.adapters.openai.handlers.ChatCompletionsHandler
import ai.dev.kit.adapters.openai.handlers.images.ImagesEditsHandler
import ai.dev.kit.adapters.openai.handlers.images.ImagesGenerationsHandler
import ai.dev.kit.adapters.openai.handlers.OpenAIApiHandler
import ai.dev.kit.adapters.openai.handlers.OpenAIApiUtils
import ai.dev.kit.adapters.openai.handlers.ResponsesApiHandler
import ai.dev.kit.adapters.openai.media.OpenAIMediaContentExtractor
import ai.dev.kit.http.protocol.Request
import ai.dev.kit.http.protocol.RequestBody
import ai.dev.kit.http.protocol.Response
import ai.dev.kit.http.protocol.Url
import ai.dev.kit.http.protocol.asFormData
import ai.dev.kit.http.protocol.asJson
import io.ktor.http.charset
import io.opentelemetry.api.trace.Span
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GenAiSystemIncubatingValues
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging


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
    // See: https://platform.openai.com/docs/api-reference/images/createEdit
    IMAGES_EDITS("images/edits");

    companion object {
        fun detect(url: Url): OpenAIApiType? {
            val route = url.pathSegments.joinToString(separator = "/")
            return entries.firstOrNull { route.contains(it.route) }
        }
    }
}

class OpenAILLMTracingAdapter : LLMTracingAdapter(genAISystem = GenAiSystemIncubatingValues.OPENAI) {
    private val logger = KotlinLogging.logger {}
    private var handler: OpenAIApiHandler? = null

    override fun getRequestBodyAttributes(span: Span, request: Request) {
        // TODO(Vladislav0Art): JBAI-18234 create handler every time a new request is processed
        if (handler == null) {
            val extractor = OpenAIMediaContentExtractor()

            handler = when (OpenAIApiType.detect(request.url)) {
                OpenAIApiType.CHAT_COMPLETIONS -> ChatCompletionsHandler(extractor)
                OpenAIApiType.RESPONSES_API -> ResponsesApiHandler(extractor)
                OpenAIApiType.IMAGES_GENERATIONS -> ImagesGenerationsHandler(extractor)
                OpenAIApiType.IMAGES_EDITS -> ImagesEditsHandler(extractor)
                else -> {
                    logger.warn { "Unknown OpenAI API detected. Defaulting to 'chat completion'." }
                    ChatCompletionsHandler(extractor)
                }
            }
        }
        handler?.handleRequestAttributes(span, request)
    }

    override fun getResultBodyAttributes(span: Span, response: Response) {
        OpenAIApiUtils.setCommonResponseAttributes(span, response)
        handler?.handleResponseAttributes(span, response)
    }

    override fun isStreamingRequest(request: Request): Boolean {
        return when (request.body) {
            is RequestBody.DataForm -> {
                val data = request.body.asFormData() ?: return false
                data.parts.filter { it.name == "stream" }.any {
                    val value = it.content.toString(it.contentType?.charset() ?: Charsets.UTF_8)
                    value.toBooleanStrictOrNull() ?: false
                }
            }
            is RequestBody.Json -> {
                val body = request.body.asJson()?.jsonObject ?: return false
                 body["stream"]?.jsonPrimitive?.boolean ?: false
            }
        }
    }

    override fun handleStreaming(span: Span, events: String) {
        handler?.handleStreaming(span, events)
    }
}
