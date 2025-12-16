package ai.jetbrains.tracy.tracing.adapters.handlers

import ai.dev.kit.adapters.LLMTracingAdapter.Companion.PayloadType
import ai.dev.kit.adapters.LLMTracingAdapter.Companion.populateUnmappedAttributes
import ai.dev.kit.adapters.handlers.EndpointApiHandler
import ai.dev.kit.adapters.media.MediaContent
import ai.dev.kit.adapters.media.MediaContentExtractor
import ai.dev.kit.adapters.media.MediaContentPart
import ai.dev.kit.adapters.media.Resource
import ai.dev.kit.common.isValidUrl
import ai.dev.kit.http.protocol.Request
import ai.dev.kit.http.protocol.Response
import ai.dev.kit.http.protocol.asJson
import io.ktor.http.*
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_USAGE_INPUT_TOKENS
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_USAGE_OUTPUT_TOKENS
import kotlinx.serialization.json.*
import mu.KotlinLogging

/**
 * Handler for OpenAI Chat Completions API
 */
internal class ChatCompletionsOpenAIApiEndpointHandler(
    private val extractor: MediaContentExtractor) : EndpointApiHandler {

    companion object {
        // https://platform.openai.com/docs/api-reference/chat/create
        private val mappedRequestAttributes: List<String> = listOf(
            "messages",
            "model",
            "tools",
            "choices",
            "temperature"
        )

        // https://platform.openai.com/docs/api-reference/chat/object
        private val mappedResponseAttributes: List<String> = listOf(
            "choices",
            "usage"
        )

        private val mappedAttributes = mappedRequestAttributes + mappedResponseAttributes

        private val logger = KotlinLogging.logger {}
    }

    override fun handleRequestAttributes(span: Span, request: Request) {
        val body = request.body.asJson()?.jsonObject ?: return
        OpenAIApiUtils.setCommonRequestAttributes(span, request)

        body["messages"]?.let {
            for ((index, message) in it.jsonArray.withIndex()) {
                val role = message.jsonObject["role"]?.jsonPrimitive?.content
                span.setAttribute("gen_ai.prompt.$index.role", role)

                // content may be of different schemas
                attachRequestContent(span, index, message.jsonObject["content"])

                // when a tool result is encountered
                if (role?.lowercase() == "tool") {
                    span.setAttribute(
                        "gen_ai.prompt.$index.tool_call_id",
                        message.jsonObject["tool_call_id"]?.jsonPrimitive?.content
                    )
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

        span.populateUnmappedAttributes(body, mappedAttributes, PayloadType.REQUEST)
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
            val mediaContent = parseMediaContent(content)
            extractor.setUploadableContentAttributes(span, field = "input", mediaContent)
            content.jsonArray.toString()
        }
        else {
            content.toString()
        }
        span.setAttribute("gen_ai.prompt.$index.content", result)
    }

    override fun handleResponseAttributes(span: Span, response: Response) {
        val body = response.body.asJson()?.jsonObject ?: return

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

        span.populateUnmappedAttributes(body, mappedAttributes, PayloadType.RESPONSE)
    }

    override fun handleStreaming(span: Span, events: String): Unit = runCatching {
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
        return@runCatching
    }.getOrElse { exception ->
        span.setStatus(StatusCode.ERROR)
        span.recordException(exception)
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

    /**
     * Extracts media content parts (images, audio, files) from JSON content.
     *
     * As for files, supports only files attached directly in the data URL (i.e., in the `file_data` field).
     * Files attached via file IDs (`file_id` field) are ignored.
     * See the schema for files: [Chat Completions API: File Content Schema](https://platform.openai.com/docs/api-reference/chat/create#chat-create-messages-user-message-content-array-of-content-parts-file-content-part-file).
     *
     * See endpoint details: [Chat Completions API](https://platform.openai.com/docs/api-reference/chat/create)
     */
    private fun parseMediaContent(content: JsonArray): MediaContent {
        val parts = buildList {
            for (part in content) {
                val type = part.jsonObject["type"]?.jsonPrimitive?.content ?: continue

                val mediaPart = when (type) {
                    "image_url" -> {
                        val url = part.jsonObject["image_url"]?.jsonObject["url"]?.jsonPrimitive?.content ?: continue
                        if (url.isValidUrl()) {
                            MediaContentPart(Resource.Url(url))
                        }
                        else if (url.startsWith("data:")) {
                            MediaContentPart(Resource.DataUrl(url))
                        }
                        else {
                            null
                        }
                    }
                    "input_audio" -> {
                        // data is base64-encoded
                        val data = part.jsonObject["input_audio"]?.jsonObject["data"]?.jsonPrimitive?.content
                            ?: continue
                        val format = part.jsonObject["input_audio"]?.jsonObject["format"]?.jsonPrimitive?.content
                            ?: continue

                        val contentType = try {
                            ContentType.parse("audio/$format")
                        } catch (err: Exception) {
                            logger.trace("Failed to parse content type: 'audio/$format'. Skipping this content part", err)
                            null
                        } ?: continue

                        MediaContentPart(resource = Resource.Base64(data, contentType))
                    }
                    "file" -> {
                        // OpenAI expects a data url with a base64-encoded PDF file
                        val fileData = part.jsonObject["file"]?.jsonObject["file_data"]?.jsonPrimitive?.content
                            ?: continue
                        MediaContentPart(Resource.DataUrl(fileData))
                    }
                    else -> null
                }

                // append media part if it's valid
                if (mediaPart != null) {
                    add(mediaPart)
                }
            }
        }

        return MediaContent(parts)
    }
}
