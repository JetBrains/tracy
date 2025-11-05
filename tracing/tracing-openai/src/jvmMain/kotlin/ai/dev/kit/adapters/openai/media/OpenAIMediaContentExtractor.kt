package ai.dev.kit.adapters.openai.media

import ai.dev.kit.adapters.media.MediaContent
import ai.dev.kit.adapters.media.MediaContentExtractor
import ai.dev.kit.adapters.media.Resource
import ai.dev.kit.common.DataUrl
import ai.dev.kit.common.parseDataUrl
import ai.dev.kit.exporters.setDataUrlAttributes
import ai.dev.kit.exporters.setUrlAttributes
import io.ktor.http.*
import io.opentelemetry.api.trace.Span
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
        for ((index, part) in content.parts.withIndex()) {
            val (resource, contentType) = part
            when (resource) {
                is Resource.Base64 -> {
                    if (contentType == null) {
                        logger.warn { "Base64-encoded data should have content type specified, got null for index $index" }
                        continue
                    }
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
