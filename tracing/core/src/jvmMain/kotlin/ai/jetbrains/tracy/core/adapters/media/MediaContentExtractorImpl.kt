package ai.jetbrains.tracy.core.adapters.media

import ai.jetbrains.tracy.core.adapters.media.DataUrl.Companion.parseDataUrl
import ai.jetbrains.tracy.core.addExceptionAttributes
import io.ktor.http.headers
import io.opentelemetry.api.trace.Span
import io.opentelemetry.sdk.trace.ReadableSpan
import mu.KotlinLogging

/**
 * Implementation of a media content extractor.
 */
class MediaContentExtractorImpl : MediaContentExtractor {
    /**
     * Sets uploadable media parts (e.g., images, audio files, and PDFs) into span attributes.
     */
    override fun setUploadableContentAttributes(
        span: Span,
        field: String,
        content: MediaContent,
    ) {
        // count the number of already installed media content parts in the given span.
        // new content parts will start with this index
        val installedMediaContentPartsCount = countAlreadyInstalledContentParts(span)

        for ((offset, part) in content.parts.withIndex()) {
            val resource = part.resource
            val index = installedMediaContentPartsCount + offset

            when (resource) {
                is Resource.Base64 -> {
                    val contentType = resource.contentType
                    val dataUrl = DataUrl(
                        mediaType = "${contentType.contentType}/${contentType.contentSubtype}",
                        headers = headers {
                            for (param in contentType.parameters) {
                                set(param.name, param.value)
                            }
                        },
                        base64 = true,
                        data = resource.base64,
                    )
                    dataUrl.setDataUrlAttributes(span, field, index)
                }

                is Resource.DataUrl -> {
                    val dataUrl = resource.dataUrl.parseDataUrl()
                    if (dataUrl != null) {
                        dataUrl.setDataUrlAttributes(span, field, index)
                    } else {
                        logger.warn { "Invalid data url, received: ${resource.dataUrl}" }
                    }
                }

                is Resource.Url -> {
                    setUrlAttributes(span, field, index, resource.url)
                }
            }
        }
    }

    /**
     * Counts the number of media content parts already installed in the given span.
     * This is done by inspecting the span's attributes for keys that match a specific regex pattern
     * indicating media content type.
     *
     * @param span The span whose attributes are inspected for already installed content parts.
     * @return The count of media content parts already installed in the provided span.
     */
    private fun countAlreadyInstalledContentParts(span: Span): Int {
        val attributes = (span as? ReadableSpan)?.attributes?.asMap()
            ?: return 0

        val prefix = UploadableMediaContentAttributeKeys.KEY_NAME_PREFIX
        val mediaContentTypeRegex = Regex("^$prefix\\.(\\d+)\\.type$")

        val contentPartsCount = attributes.keys.count {
            it.key.matches(mediaContentTypeRegex)
        }

        return contentPartsCount
    }

    /**
     * Installs URL-related fields for the uploadable media content into the span
     *
     *
     * @see UploadableMediaContentAttributeKeys
     */
    private fun setUrlAttributes(
        span: Span,
        field: String,
        index: Int,
        url: String,
    ) {
        if (!url.isValidUrl()) {
            span.addExceptionAttributes(IllegalArgumentException("Expected a valid URL, received: $url"))
        }
        val keys = UploadableMediaContentAttributeKeys.forIndex(index)

        span.setAttribute(keys.type, SupportedMediaContentTypes.URL.type)
        span.setAttribute(keys.field, field)
        span.setAttribute(keys.url, url)
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}