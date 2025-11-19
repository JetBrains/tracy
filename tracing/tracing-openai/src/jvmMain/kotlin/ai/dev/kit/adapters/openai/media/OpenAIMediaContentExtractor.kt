package ai.dev.kit.adapters.openai.media

import ai.dev.kit.adapters.media.MediaContent
import ai.dev.kit.adapters.media.MediaContentExtractor
import ai.dev.kit.adapters.media.Resource
import ai.dev.kit.common.DataUrl
import ai.dev.kit.common.parseDataUrl
import ai.dev.kit.exporters.UploadableMediaContentAttributeKeys
import ai.dev.kit.exporters.setDataUrlAttributes
import ai.dev.kit.exporters.setUrlAttributes
import io.ktor.http.*
import io.opentelemetry.api.trace.Span
import io.opentelemetry.sdk.trace.ReadableSpan
import mu.KotlinLogging


/**
 * OpenAI-oriented extractor of media content.
 */
internal class OpenAIMediaContentExtractor : MediaContentExtractor {
    /**
     * Sets uploadable media parts (e.g., images, audio files, and PDFs)
     * of the request into span attributes.
     *
     * See [OpenAI Chat Completions](https://platform.openai.com/docs/api-reference/chat/create#chat-create-messages-user-message-content)
     *
     * See [OpenAI Responses API](https://platform.openai.com/docs/api-reference/responses/create#responses_create-input)
     */
    override fun setUploadableContentAttributes(
        span: Span,
        field: String,
        content: MediaContent,
    ) {
        // count the number of already installed media content parts in the given span.
        // new content parts will start with this index
        val installedMediaContentPartsCount = run {
            val attributes = (span as? ReadableSpan)?.attributes?.asMap()
                ?: return@run 0

            val prefix = UploadableMediaContentAttributeKeys.KEY_NAME_PREFIX
            val mediaContentTypeRegex = Regex("^$prefix\\.(\\d+)\\.type$")

            val contentPartsCount = attributes.keys.count {
                it.key.matches(mediaContentTypeRegex)
            }
            contentPartsCount
        }

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
                    setDataUrlAttributes(span, field, index, dataUrl)
                }
                is Resource.DataUrl -> {
                    val dataUrl = resource.dataUrl.parseDataUrl()
                    if (dataUrl != null) {
                        setDataUrlAttributes(span, field, index, dataUrl)
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

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
