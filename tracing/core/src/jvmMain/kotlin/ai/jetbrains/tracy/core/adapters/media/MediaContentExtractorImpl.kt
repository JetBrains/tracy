package ai.jetbrains.tracy.core.adapters.media

import ai.jetbrains.tracy.core.adapters.media.DataUrl.Companion.parseInlineDataUrl
import io.ktor.http.headers
import io.opentelemetry.api.trace.Span
import io.opentelemetry.sdk.trace.ReadableSpan
import mu.KotlinLogging

/**
 * Implementation of a media content extractor.
 */
class MediaContentExtractorImpl : MediaContentExtractor {
    private val logger = KotlinLogging.logger {}

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
                    span.setDataUrlAttributes(dataUrl, field, index)
                }

                is Resource.InlineDataUrl -> {
                    val dataUrl = resource.parseInlineDataUrl()
                    if (dataUrl != null) {
                        span.setDataUrlAttributes(dataUrl, field, index)
                    } else {
                        logger.warn { "Invalid data url, received: ${resource.inlineDataUrl}" }
                    }
                }

                is Resource.Url -> {
                    span.setUrlAttributes(resource.url, field, index)
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
}