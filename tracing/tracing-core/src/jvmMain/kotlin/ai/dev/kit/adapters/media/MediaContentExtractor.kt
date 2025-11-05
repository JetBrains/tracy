package ai.dev.kit.adapters.media

import ai.dev.kit.exporters.UploadableMediaContentAttributeKeys
import io.ktor.http.ContentType
import io.opentelemetry.api.trace.Span

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
        content: MediaContent,
    )
}

data class MediaContent(
    val parts: List<MediaContentPart>
)

data class MediaContentPart(
    val resource: Resource,
    val contentType: ContentType? = null,
) {
    init {
        if (resource is Resource.Base64 && contentType == null) {
            error("Base64-encoded data should have content type specified, got null")
        }
    }
}

sealed class Resource {
    data class Url(val url: String) : Resource()
    data class DataUrl(val dataUrl: String) : Resource()
    data class Base64(val base64: String) : Resource()
}