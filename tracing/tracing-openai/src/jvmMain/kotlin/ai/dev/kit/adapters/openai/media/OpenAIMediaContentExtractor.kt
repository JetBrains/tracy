package ai.dev.kit.adapters.openai.media

import ai.dev.kit.adapters.media.MediaContentExtractor
import ai.dev.kit.common.DataUrl
import ai.dev.kit.common.isValidUrl
import ai.dev.kit.common.parseDataUrl
import ai.dev.kit.exporters.setDataUrlAttributes
import ai.dev.kit.exporters.setUrlAttributes
import io.opentelemetry.api.trace.Span
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging
import io.ktor.http.Headers

/**
 * Types of media content supported by OpenAI.
 *
 * @see ChatCompletions
 * @see ResponsesApi
 */
internal sealed class SupportedContentTypeTags(
    val image: String,
    val audio: String?,
    val file: String,
) {
    /**
     * See details: [Chat Completions API Docs](https://platform.openai.com/docs/api-reference/chat/create)
     */
    object ChatCompletions : SupportedContentTypeTags(
        image = "image_url",
        audio = "input_audio",
        file = "file",
    )

    /**
     * See details: [Responses API Docs](https://platform.openai.com/docs/api-reference/responses/create#responses_create-input-input_item_list-input_message-content)
     */
    object ResponsesApi : SupportedContentTypeTags(
        image = "input_image",
        audio = null,
        file = "input_file",
    )
}

/**
 * OpenAI-oriented extract of media content
 *
 * @see ChatCompletionsMediaContentExtractor
 * @see ResponsesMediaContentExtractor
 * @see MediaContentExtractor
 */
internal abstract class OpenAIMediaContentExtractor(
    private val tags: SupportedContentTypeTags
) : MediaContentExtractor {
    protected val logger = KotlinLogging.logger {}

    /**
     * Sets uploadable media parts (e.g., images, audio files, and PDFs)
     * of the request into span attributes.
     *
     * See [OpenAI Chat Completions](https://platform.openai.com/docs/api-reference/chat/create#chat-create-messages-user-message-content)
     *
     * See [OpenAI Responses API](https://platform.openai.com/docs/api-reference/responses/create#responses_create-input)
     *
     * @see ChatCompletionsMediaContentExtractor
     * @see ResponsesMediaContentExtractor
     */
    override fun setUploadableContentAttributes(
        span: Span,
        field: String,
        content: JsonArray,
    ) {
        val supportedTypes = listOf(tags.image, tags.audio, tags.file)

        content.asSequence()
            .map { it.jsonObject }
            .mapNotNull { obj ->
                val type = obj["type"]?.jsonPrimitive?.content
                if (type != null && type in supportedTypes) type to obj else null
            }
            .forEachIndexed { index, (type, obj) ->
                when (type) {
                    tags.image -> setImageUrlAttributes(span, field, index, obj)
                    tags.audio -> setAudioInputAttributes(span, field, index, obj)
                    tags.file -> setFileInputAttributes(span, field, index, obj)
                }
            }
    }

    protected fun setImageUrlAttributes(
        span: Span, field: String, index: Int, contentItem: JsonObject) {
        val url = extractImageUrl(contentItem) ?: return

        if (url.isValidUrl()) {
            setUrlAttributes(span, field, index, url)
        }
        else if (url.startsWith("data:")) {
            // received data URL: data in the URL is expected to be base64-encoded
            val dataUrl = url.parseDataUrl() ?: return
            setDataUrlAttributes(span, field, index, dataUrl)
        }
        else {
            logger.warn { "Image url is not a valid type, either a URL or data URL expected. Received: $url" }
        }
    }

    protected abstract fun extractImageUrl(contentItem: JsonObject): String?

    protected abstract fun setAudioInputAttributes(
        span: Span, field: String, index: Int, contentItem: JsonObject)

    protected abstract fun setFileInputAttributes(
        span: Span, field: String, index: Int, contentItem: JsonObject)
}

/**
 * Implementation of media content extractor for Chat Completions API.
 *
 * See details: [Chat Completions API](https://platform.openai.com/docs/api-reference/chat/create)
 */
internal class ChatCompletionsMediaContentExtractor : OpenAIMediaContentExtractor(
    tags = SupportedContentTypeTags.ChatCompletions,
) {
    override fun setAudioInputAttributes(
        span: Span, field: String, index: Int, contentItem: JsonObject) {
        // data is base64-encoded
        val data = contentItem["input_audio"]?.jsonObject["data"]?.jsonPrimitive?.content
            ?: return
        val format = contentItem["input_audio"]?.jsonObject["format"]?.jsonPrimitive?.content
            ?: return
        val contentType = "audio/$format"

        val dataUrl = DataUrl(
            mediaType = contentType,
            headers = Headers.Empty,
            base64 = true,
            data = data,
        )
        setDataUrlAttributes(span, field, index, dataUrl)
    }

    /**
     * Supports only files attached directly in the data URL (i.e., in the `file_data` field).
     *
     * Files attached via file IDs (`file_id` field) are ignored.
     *
     * See [OpenAI Chat Completions](https://platform.openai.com/docs/api-reference/chat/create#chat-create-messages-user-message-content-array-of-content-parts-file-content-part-file).
     */
    override fun setFileInputAttributes(
        span: Span, field: String, index: Int, contentItem: JsonObject) {
        // OpenAI expects a data url with a base64-encoded PDF file
        val fileData = contentItem["file"]?.jsonObject["file_data"]?.jsonPrimitive?.content
            ?: return
        val dataUrl = fileData.parseDataUrl() ?: return
        setDataUrlAttributes(span, field, index, dataUrl)
    }

    override fun extractImageUrl(contentItem: JsonObject): String? {
        return contentItem["image_url"]?.jsonObject["url"]?.jsonPrimitive?.content
    }
}

/**
 * Implementation of media content extractor for Responses API.
 *
 * See details: [Responses API](https://platform.openai.com/docs/api-reference/responses/create)
 */
internal class ResponsesMediaContentExtractor : OpenAIMediaContentExtractor(
    tags = SupportedContentTypeTags.ResponsesApi
) {
    override fun setAudioInputAttributes(
        span: Span, field: String, index: Int, contentItem: JsonObject) {
        // no-op for responses API
    }

    override fun setFileInputAttributes(
        span: Span, field: String, index: Int, contentItem: JsonObject) {

        if ("file_url" in contentItem) {
            val url = contentItem["file_url"]?.jsonPrimitive?.content ?: return
            if (url.isValidUrl()) {
                setUrlAttributes(span, field, index, url)
            }
            else {
                logger.warn { "File url is invalid. Received: $url" }
            }
        }
        else if ("file_data" in contentItem) {
            val dataUrl = contentItem["file_data"]?.jsonPrimitive?.content?.parseDataUrl() ?: return
            setDataUrlAttributes(span, field, index, dataUrl)
        }
        else {
            logger.warn { "Unknown content item structure for file input at index $index" }
        }
    }

    override fun extractImageUrl(contentItem: JsonObject): String? {
        return contentItem["image_url"]?.jsonPrimitive?.content
    }
}