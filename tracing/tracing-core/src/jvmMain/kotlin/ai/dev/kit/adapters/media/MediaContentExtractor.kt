package ai.dev.kit.adapters.media

import ai.dev.kit.exporters.UploadableMediaContentAttributeKeys
import io.opentelemetry.api.trace.Span
import kotlinx.serialization.json.JsonArray

/**
 * Extracts media content (e.g., images, audio, files) from a JSON array
 * (likely, requests to/responses of LLM-specific APIs, e.g., OpenAI APIs)
 * and attaches it to the span under certain keys described by [UploadableMediaContentAttributeKeys].
 *
 * @see UploadableMediaContentAttributeKeys
 * @see ai.dev.kit.exporters.setUrlAttributes
 * @see ai.dev.kit.exporters.setDataUrlAttributes
 */
interface MediaContentExtractor {
    fun setUploadableContentAttributes(
        span: Span,
        field: String,
        content: JsonArray,
    )
}