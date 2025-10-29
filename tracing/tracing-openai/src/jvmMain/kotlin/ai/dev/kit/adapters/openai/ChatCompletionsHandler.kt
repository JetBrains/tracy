package ai.dev.kit.adapters.openai

import ai.dev.kit.adapters.Url
import ai.dev.kit.common.DataUrl
import ai.dev.kit.common.isValidUrl
import ai.dev.kit.common.parseDataUrl
import ai.dev.kit.exporters.SupportedMediaContentTypes
import ai.dev.kit.exporters.UploadableMediaContentAttributeKeys
import io.opentelemetry.api.trace.Span
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_USAGE_INPUT_TOKENS
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_USAGE_OUTPUT_TOKENS
import kotlinx.serialization.json.*
import mu.KotlinLogging

/**
 * Handler for OpenAI Chat Completions API
 */
internal class ChatCompletionsHandler : OpenAIApiHandler {
    companion object {
        private val logger = KotlinLogging.logger {}

        enum class MediaContentTypes(val tag: String) {
            ImageUrl("image_url"),
            InputAudio("input_audio"),
            File("file"),
        }

        private const val WARNING_URL_LENGTH_LIMIT = 200
    }

    override fun handleRequestAttributes(span: Span, url: Url, body: JsonObject) {
        OpenAIApiUtils.setCommonRequestAttributes(span, body)

        body["messages"]?.let {
            for ((index, message) in it.jsonArray.withIndex()) {
                val role = message.jsonObject["role"]?.jsonPrimitive?.content
                span.setAttribute("gen_ai.prompt.$index.role", role)

                // content may be of different schemas
                attachRequestContent(span, index, message.jsonObject["content"])

                // when a tool result is encountered
                if (role?.lowercase() == "tool") {
                    span.setAttribute("gen_ai.prompt.$index.tool_call_id", message.jsonObject["tool_call_id"]?.jsonPrimitive?.content)
                }
            }
        }

        // See: https://platform.openai.com/docs/api-reference/chat/create
        body["tools"]?.let { tools ->
            if (tools is JsonArray) {
                for ((index, tool) in tools.jsonArray.withIndex()) {
                    span.setAttribute("gen_ai.tool.$index.type", tool.jsonObject["type"]?.jsonPrimitive?.content)
                    tool.jsonObject["function"]?.jsonObject?.let {
                        span.setAttribute("gen_ai.tool.$index.name", it["name"]?.jsonPrimitive?.content)
                        span.setAttribute("gen_ai.tool.$index.description", it["description"]?.jsonPrimitive?.content)
                        span.setAttribute("gen_ai.tool.$index.parameters", it["parameters"]?.jsonObject?.toString())
                        span.setAttribute("gen_ai.tool.$index.strict", it["strict"]?.jsonPrimitive?.boolean.toString())
                    }
                }
            }
        }
    }

    /**
     * Inserts the message content depending on its type.
     *
     * The content can be either a normal text (i.e., a string) or
     * an array when a media input is attached (e.g., images, audio, and files).
     *
     * For more details on possible content structures,
     * see [User Message Content Description](https://platform.openai.com/docs/api-reference/chat/create#chat-create-messages-user-message-content).
     *
     * Additionally, see: [OpenAI Chat Completions](https://platform.openai.com/docs/api-reference/chat/create#chat-create-messages)
     */
    private fun attachRequestContent(
        span: Span,
        index: Int,
        content: JsonElement?,
    ) {
        if (content == null) {
            span.setAttribute("gen_ai.prompt.$index.content", null)
            return
        }

        // See content types: https://platform.openai.com/docs/api-reference/chat/create#chat-create-messages-user-message-content
        val result: String = if (content is JsonPrimitive) {
            content.jsonPrimitive.content
        }
        else if (content is JsonArray) {
            // array that contains entries of either image, audio, file or normal text
            setUploadableContentAttributes(span, content, field = "input")
            content.jsonArray.toString()
        }
        else {
            content.toString()
        }
        span.setAttribute("gen_ai.prompt.$index.content", result)
    }

    /**
     * Sets uploadable media parts (e.g., images, audio files, and PDFs)
     * of the request into span attributes.
     *
     * See [OpenAI Documentation](https://platform.openai.com/docs/api-reference/chat/create#chat-create-messages-user-message-content)
     */
    private fun setUploadableContentAttributes(
        span: Span,
        content: JsonArray,
        field: String,
    ) {
        var index = 0
        for (item in content) {
            val type = item.jsonObject["type"]?.jsonPrimitive?.content
                ?: continue
            when (type) {
                MediaContentTypes.ImageUrl.tag -> setImageUrlAttributes(span, index, item.jsonObject)
                MediaContentTypes.InputAudio.tag -> setAudioInputAttributes(span, index, item.jsonObject)
                MediaContentTypes.File.tag -> setFileInputAttributes(span, index, item.jsonObject)
            }
            span.setAttribute(UploadableMediaContentAttributeKeys.field(index), field)
            ++index
        }
    }

    private fun setImageUrlAttributes(span: Span, index: Int, contentItem: JsonObject) {
        val url = contentItem["image_url"]?.jsonObject["url"]?.jsonPrimitive?.content ?: return

        if (url.isValidUrl()) {
            // normal URL
            span.setAttribute(UploadableMediaContentAttributeKeys.type(index), SupportedMediaContentTypes.URL.type)
            span.setAttribute(UploadableMediaContentAttributeKeys.url(index), url)
        }
        else if (url.startsWith("data:")) {
            // received data URL: data in the URL is expected to be base64 encoded
            val dataUrl = url.parseDataUrl() ?: return
            setDataUrlAttributes(span, index, dataUrl)
        }
        else {
            logger.warn { "Image url is not a valid type, either a URL or data URL expected. Received: $url" }
        }
    }

    private fun setAudioInputAttributes(span: Span, index: Int, contentItem: JsonObject) {
        // data is base64-encoded
        val data = contentItem["input_audio"]?.jsonObject["data"]?.jsonPrimitive?.content
            ?: return
        val format = contentItem["input_audio"]?.jsonObject["format"]?.jsonPrimitive?.content
            ?: return
        val contentType = "audio/$format"

        span.setAttribute(UploadableMediaContentAttributeKeys.type(index), SupportedMediaContentTypes.BASE64.type)
        span.setAttribute(UploadableMediaContentAttributeKeys.contentType(index), contentType)
        span.setAttribute(UploadableMediaContentAttributeKeys.data(index), data)
    }

    /**
     * Supports only files attached directly in the data URL (i.e., in the `file_data` field).
     *
     * Files attached via file IDs (`file_id` field) are ignored.
     *
     * See [OpenAI Documentation](https://platform.openai.com/docs/api-reference/chat/create#chat-create-messages-user-message-content-array-of-content-parts-file-content-part-file).
     */
    private fun setFileInputAttributes(span: Span, index: Int, contentItem: JsonObject) {
        // OpenAI expects a data url with a base64-encoded PDF file
        val fileData = contentItem["file"]?.jsonObject["file_data"]?.jsonPrimitive?.content
            ?: return
        val dataUrl = fileData.parseDataUrl() ?: return
        setDataUrlAttributes(span, index, dataUrl)
    }

    /**
     * Sets base64-related attributes into the span, ensuring that [dataUrl]
     * contains the data in the base64-encoded format.
     *
     * @see setImageUrlAttributes
     * @see setFileInputAttributes
     * @see UploadableMediaContentAttributeKeys
     */
    private fun setDataUrlAttributes(span: Span, index: Int, dataUrl: DataUrl) {
        if (!dataUrl.base64) {
            val str = dataUrl.asString()
            val trimmed = if (str.length < WARNING_URL_LENGTH_LIMIT) str else str.substring(0, WARNING_URL_LENGTH_LIMIT) + "..."
            logger.warn { "Expect base64 encoding for the data url, received '$trimmed'" }
            return
        }
        span.setAttribute(UploadableMediaContentAttributeKeys.type(index), SupportedMediaContentTypes.BASE64.type)
        span.setAttribute(UploadableMediaContentAttributeKeys.contentType(index), dataUrl.mediaType)
        span.setAttribute(UploadableMediaContentAttributeKeys.data(index), dataUrl.data)
    }

    override fun handleResponseAttributes(span: Span, body: JsonObject) {
        println("RESPONSE BODY: $body")

        body["choices"]?.let {
            for ((index, choice) in it.jsonArray.withIndex()) {
                val index = choice.jsonObject["index"]?.jsonPrimitive?.intOrNull ?: index

                span.setAttribute(
                    "gen_ai.completion.$index.finish_reason",
                    choice.jsonObject["finish_reason"]?.jsonPrimitive?.content
                )

                choice.jsonObject["message"]?.jsonObject?.let { message ->
                    span.setAttribute(
                        "gen_ai.completion.$index.role",
                        message.jsonObject["role"]?.jsonPrimitive?.content
                    )
                    span.setAttribute("gen_ai.completion.$index.content", message.jsonObject["content"]?.toString())

                    // See: https://platform.openai.com/docs/api-reference/chat/object
                    message.jsonObject["tool_calls"]?.let {
                        // sometimes, this prop is explicitly set to null, hence, being JsonNull.
                        // therefore, we check for the required array type
                        if (it is JsonArray) {
                            for ((toolCallIndex, toolCall) in it.jsonArray.withIndex()) {
                                span.setAttribute(
                                    "gen_ai.completion.$index.tool.$toolCallIndex.call.id",
                                    toolCall.jsonObject["id"]?.jsonPrimitive?.content
                                )
                                span.setAttribute(
                                    "gen_ai.completion.$index.tool.$toolCallIndex.call.type",
                                    toolCall.jsonObject["type"]?.jsonPrimitive?.content
                                )

                                toolCall.jsonObject["function"]?.jsonObject?.let {
                                    span.setAttribute(
                                        "gen_ai.completion.$index.tool.$toolCallIndex.name",
                                        it["name"]?.jsonPrimitive?.content
                                    )
                                    span.setAttribute(
                                        "gen_ai.completion.$index.tool.$toolCallIndex.arguments",
                                        it["arguments"]?.jsonPrimitive?.content
                                    )
                                }
                            }
                        }
                    }

                    span.setAttribute(
                        "gen_ai.completion.$index.annotations",
                        message.jsonObject["annotations"].toString()
                    )
                }
            }
        }

        body["usage"]?.let { usage ->
            setUsageAttributes(span, usage.jsonObject)
        }
    }

    override fun handleStreaming(span: Span, events: String) {
        var role: String? = null
        val out = buildString {
            for (line in events.lineSequence()) {
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()

                val e = runCatching { Json.parseToJsonElement(data).jsonObject }.getOrNull() ?: continue
                val choice = e["choices"]?.jsonArray?.firstOrNull()?.jsonObject ?: continue
                val delta = choice["delta"]?.jsonObject ?: continue

                if (role == null) role = delta["role"]?.jsonPrimitive?.content
                delta["content"]?.jsonPrimitive?.content?.let { append(it) }
            }
        }

        if (out.isNotEmpty()) span.setAttribute("gen_ai.completion.0.content", out)
        role?.let { span.setAttribute("gen_ai.completion.0.role", it) }
    }

    /**
     * Sets usage attributes (prompt_tokens/completion_tokens)
     */
    private fun setUsageAttributes(span: Span, usage: JsonObject) {
        usage["prompt_tokens"]?.jsonPrimitive?.intOrNull?.let {
            span.setAttribute(GEN_AI_USAGE_INPUT_TOKENS, it)
        }
        usage["completion_tokens"]?.jsonPrimitive?.intOrNull?.let {
            span.setAttribute(GEN_AI_USAGE_OUTPUT_TOKENS, it)
        }
    }
}
