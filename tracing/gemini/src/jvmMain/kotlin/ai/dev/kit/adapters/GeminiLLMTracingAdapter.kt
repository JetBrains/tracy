package ai.dev.kit.adapters

import ai.dev.kit.adapters.handlers.GeminiContentGenHandler
import ai.dev.kit.adapters.handlers.GeminiImagenHandler
import ai.dev.kit.adapters.media.MediaContentExtractor
import ai.dev.kit.http.protocol.Request
import ai.dev.kit.http.protocol.Response
import ai.dev.kit.http.protocol.Url
import ai.dev.kit.http.protocol.asJson
import io.opentelemetry.api.trace.Span
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import mu.KotlinLogging

class GeminiLLMTracingAdapter(
    private val extractor: MediaContentExtractor
) : LLMTracingAdapter(genAISystem = GenAiSystemIncubatingValues.GEMINI) {
    override fun getRequestBodyAttributes(span: Span, request: Request) {
        val (model, operation) = request.url.modelAndOperation()

        model?.let { span.setAttribute(GEN_AI_REQUEST_MODEL, model) }
        operation?.let { span.setAttribute(GEN_AI_OPERATION_NAME, operation) }

        val handler = when {
            request.isImagenRequest() -> GeminiImagenHandler(extractor)
            else -> GeminiContentGenHandler(extractor)
        }
        handler.handleRequestAttributes(span, request)
    }

    override fun getResponseBodyAttributes(span: Span, response: Response) {
        val body = response.body.asJson()?.jsonObject ?: return

        val handler = when {
            isImagenResponse(body) -> GeminiImagenHandler(extractor)
            else -> GeminiContentGenHandler(extractor)
        }
        handler.handleResponseAttributes(span, response)
    }

    // streaming is not supported
    override fun isStreamingRequest(request: Request) = false
    override fun handleStreaming(span: Span, url: Url, events: String) {
        // TODO: when PR (https://github.com/JetBrains/tracy/pull/124) is merged, select handler using `url`
    }

    private fun Url.modelAndOperation(): Pair<String?, String?> {
        // url ends with `[model]:[operation]`
        return this.pathSegments.lastOrNull()?.split(":")
            ?.let { it.firstOrNull() to it.lastOrNull() } ?: (null to null)
    }

    private fun Request.isImagenRequest(): Boolean {
        val (model, operation) = this.url.modelAndOperation()
        return (model?.startsWith("imagen") == true) && (operation == "predict")
    }

    // TODO: when PR (https://github.com/JetBrains/tracy/pull/124) is merged, write `isImagenResponse()` using `url`
    private fun isImagenResponse(body: JsonObject): Boolean {
        return "predictions" in body
    }

    companion object {
        private val logger = KotlinLogging.logger {}

        private val mappedRequestAttributes: List<String> = listOf(
            "contents",
            "tools",
            "generationConfig"
        )

        private val mappedResponseAttributes: List<String> = listOf(
            "responseId",
            "modelVersion",
            "candidates",
            "usageMetadata"
        )

        private val mappedAttributes = mappedRequestAttributes + mappedResponseAttributes
    }
    // span.populateUnmappedAttributes(body, mappedAttributes, PayloadType.RESPONSE)
}