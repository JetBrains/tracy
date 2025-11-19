package ai.dev.kit.adapters

import ai.dev.kit.adapters.openai.handlers.ChatCompletionsHandler
import ai.dev.kit.adapters.openai.handlers.images.ImagesEditsHandler
import ai.dev.kit.adapters.openai.handlers.images.ImagesGenerationsHandler
import ai.dev.kit.adapters.openai.handlers.OpenAIApiHandler
import ai.dev.kit.adapters.openai.handlers.OpenAIApiUtils
import ai.dev.kit.adapters.openai.handlers.ResponsesApiHandler
import ai.dev.kit.adapters.media.MediaContentExtractorImpl
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
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock


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
    private val handlerLock = ReentrantLock()
    private var handler: OpenAIApiHandler? = null
    private var previousApiType: OpenAIApiType? = null

    override fun getRequestBodyAttributes(span: Span, request: Request) {
        val currentHandler = handlerLock.withLock {
            adaptHandlerToEndpoint(request.url)
            handler
        }
        currentHandler?.handleRequestAttributes(span, request)
    }

    override fun getResponseBodyAttributes(span: Span, response: Response) {
        val currentHandler = handlerLock.withLock { handler }

        OpenAIApiUtils.setCommonResponseAttributes(span, response)
        currentHandler?.handleResponseAttributes(span, response)
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
        val currentHandler = handlerLock.withLock { handler }
        currentHandler?.handleStreaming(span, events)
    }

    /**
     * Updates the [handler] to the corresponding OpenAI API endpoint based on the provided URL. This method
     * detects the API type from the URL and updates the [handler] implementation accordingly.
     *
     * When [previousApiType] matches the detected API type, the existing [handler] is used.
     * Otherwise, [handler] is set to a new instance according to the detected API type.
     *
     * **This function should be called under the lock held**.
     *
     * @param url The URL used to determine the appropriate OpenAI API endpoint and adapt the [handler].
     */
    private fun adaptHandlerToEndpoint(url: Url) {
        val apiType = OpenAIApiType.detect(url)

        if (previousApiType == null || previousApiType != apiType) {
            val extractor = MediaContentExtractorImpl()

            handler = when (apiType) {
                OpenAIApiType.CHAT_COMPLETIONS -> ChatCompletionsHandler(extractor)
                OpenAIApiType.RESPONSES_API -> ResponsesApiHandler(extractor)
                OpenAIApiType.IMAGES_GENERATIONS -> ImagesGenerationsHandler(extractor)
                OpenAIApiType.IMAGES_EDITS -> ImagesEditsHandler(extractor)
                null -> {
                    logger.warn { "Unknown OpenAI API detected. Defaulting to 'chat completion'." }
                    ChatCompletionsHandler(extractor)
                }
            }
            previousApiType = apiType
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
