package ai.jetbrains.tracy.tracing.adapters

import ai.dev.kit.adapters.LLMTracingAdapter
import ai.dev.kit.adapters.handlers.EndpointApiHandler
import ai.jetbrains.tracy.tracing.adapters.handlers.ChatCompletionsOpenAIApiEndpointHandler
import ai.jetbrains.tracy.tracing.adapters.handlers.images.ImagesCreateEditOpenAIApiEndpointHandler
import ai.jetbrains.tracy.tracing.adapters.handlers.images.ImagesCreateOpenAIApiEndpointHandler
import ai.jetbrains.tracy.tracing.adapters.handlers.OpenAIApiUtils
import ai.jetbrains.tracy.tracing.adapters.handlers.ResponsesOpenAIApiEndpointHandler
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
import java.util.concurrent.ConcurrentHashMap


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

/**
 * Processes OpenAI API calls and extracts relevant information as span attributes.
 */
class OpenAILLMTracingAdapter : LLMTracingAdapter(genAISystem = GenAiSystemIncubatingValues.OPENAI) {
    private val handlers = ConcurrentHashMap<OpenAIApiType, EndpointApiHandler>()

    override fun getRequestBodyAttributes(span: Span, request: Request) {
        val handler = handlerFor(request.url)
        handler.handleRequestAttributes(span, request)
    }

    override fun getResponseBodyAttributes(span: Span, response: Response) {
        val handler = handlerFor(response.url)
        OpenAIApiUtils.setCommonResponseAttributes(span, response)
        handler.handleResponseAttributes(span, response)
    }

    override fun getSpanName(request: Request) = "OpenAI-generation"

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

    override fun handleStreaming(span: Span, url: Url, events: String) {
        val handler = handlerFor(url)
        handler.handleStreaming(span, events)
    }

    /**
     * Determines the appropriate handler for an OpenAI API based on the given URL.
     *
     * @param endpoint The URL used to detect the API type and determine the corresponding handler.
     * @return An instance of [EndpointApiHandler] that is capable of handling requests for the detected API type.
     */
    private fun handlerFor(endpoint: Url): EndpointApiHandler {
        val apiType = OpenAIApiType.detect(endpoint)
        val extractor = MediaContentExtractorImpl()

        val handler = when (apiType) {
            OpenAIApiType.CHAT_COMPLETIONS -> handlers.getOrPut(OpenAIApiType.CHAT_COMPLETIONS) {
                ChatCompletionsOpenAIApiEndpointHandler(extractor)
            }
            OpenAIApiType.RESPONSES_API -> handlers.getOrPut(OpenAIApiType.RESPONSES_API) {
                ResponsesOpenAIApiEndpointHandler(extractor)
            }
            OpenAIApiType.IMAGES_GENERATIONS -> handlers.getOrPut(OpenAIApiType.IMAGES_GENERATIONS) {
                ImagesCreateOpenAIApiEndpointHandler(extractor)
            }
            OpenAIApiType.IMAGES_EDITS -> handlers.getOrPut(OpenAIApiType.IMAGES_EDITS) {
                ImagesCreateEditOpenAIApiEndpointHandler(extractor)
            }
            null -> handlers.getOrPut(OpenAIApiType.CHAT_COMPLETIONS) {
                logger.warn { "Unknown OpenAI API detected. Defaulting to 'chat completion'." }
                ChatCompletionsOpenAIApiEndpointHandler(extractor)
            }
        }
        return handler
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
