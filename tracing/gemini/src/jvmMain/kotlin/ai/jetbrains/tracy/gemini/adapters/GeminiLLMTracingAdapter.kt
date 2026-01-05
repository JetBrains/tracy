package ai.jetbrains.tracy.gemini.adapters

import ai.dev.kit.adapters.LLMTracingAdapter
import ai.dev.kit.adapters.handlers.EndpointApiHandler
import ai.jetbrains.tracy.gemini.adapters.handlers.GeminiContentGenHandler
import ai.jetbrains.tracy.gemini.adapters.handlers.GeminiImagenHandler
import ai.dev.kit.adapters.media.MediaContentExtractor
import ai.dev.kit.adapters.media.MediaContentExtractorImpl
import ai.dev.kit.http.protocol.Request
import ai.dev.kit.http.protocol.Response
import ai.dev.kit.http.protocol.Url
import io.opentelemetry.api.trace.Span
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.*

class GeminiLLMTracingAdapter() : LLMTracingAdapter(genAISystem = GenAiSystemIncubatingValues.GEMINI) {
    override fun getRequestBodyAttributes(span: Span, request: Request) {
        val (model, operation) = request.url.modelAndOperation()

        model?.let { span.setAttribute(GEN_AI_REQUEST_MODEL, model) }
        operation?.let { span.setAttribute(GEN_AI_OPERATION_NAME, operation) }

        val handler = selectHandler(request.url)
        handler.handleRequestAttributes(span, request)
    }

    override fun getResponseBodyAttributes(span: Span, response: Response) {
        val handler = selectHandler(response.url)
        handler.handleResponseAttributes(span, response)
    }

    override fun getSpanName(request: Request) = "Gemini-generation"

    // streaming is not supported
    override fun isStreamingRequest(request: Request) = false
    override fun handleStreaming(span: Span, url: Url, events: String) {
        val handler = selectHandler(url)
        handler.handleStreaming(span, events)
    }

    private fun selectHandler(url: Url): EndpointApiHandler = when {
        url.isImagenUrl() -> GeminiImagenHandler(extractor)
        else -> GeminiContentGenHandler(extractor)
    }

    private fun Url.modelAndOperation(): Pair<String?, String?> {
        // url ends with `[model]:[operation]`
        return this.pathSegments.lastOrNull()?.split(":")
            ?.let { it.firstOrNull() to it.lastOrNull() } ?: (null to null)
    }

    private fun Url.isImagenUrl(): Boolean {
        val (model, operation) = this.modelAndOperation()
        return (model?.startsWith("imagen") == true) && (operation == "predict")
    }

    private companion object {
        private val extractor: MediaContentExtractor = MediaContentExtractorImpl()
    }
}