/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.anthropic.adapters

import org.jetbrains.ai.tracy.core.adapters.LLMTracingAdapter
import org.jetbrains.ai.tracy.core.adapters.LLMTracingAdapter.Companion.PayloadType
import org.jetbrains.ai.tracy.core.adapters.media.*
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpRequest
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpResponse
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpUrl
import org.jetbrains.ai.tracy.core.http.protocol.asJson
import org.jetbrains.ai.tracy.core.policy.ContentKind
import org.jetbrains.ai.tracy.core.policy.contentTracingAllowed
import org.jetbrains.ai.tracy.core.policy.orRedactedInput
import org.jetbrains.ai.tracy.core.policy.orRedactedOutput
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.*
import kotlinx.serialization.json.*
import mu.KotlinLogging

/**
 * Tracing adapter for Anthropic Claude API.
 *
 * Parses Anthropic Messages API requests and responses to extract telemetry data including
 * model parameters, messages, tool definitions, tool calls, usage statistics, and media content.
 * Supports both text and multimodal inputs (images, documents).
 *
 * ## Example Usage
 * ```kotlin
 * val client = instrument(HttpClient(), AnthropicLLMTracingAdapter())
 * client.post("https://api.anthropic.com/v1/messages") {
 *     header("x-api-key", apiKey)
 *     header("anthropic-version", "2023-06-01")
 *     setBody("""
 *         {
 *             "max_tokens": 1024,
 *             "messages": [{"content": "Hello!", "role": "user"}],
 *             "model": "claude-3-7-sonnet-latest"
 *         }
 *     """)
 * }
 * // Automatically traces request/response with tool calls and media content
 * ```
 *
 * See: [Anthropic Messages API](https://docs.claude.com/en/api/messages)
 */
class AnthropicLLMTracingAdapter : LLMTracingAdapter(genAISystem = GenAiSystemIncubatingValues.ANTHROPIC) {
    override fun getRequestBodyAttributes(span: Span, request: TracyHttpRequest) {
        val body = request.body.asJson()?.jsonObject ?: return

        body["temperature"]?.jsonPrimitive?.let { span.setAttribute(GEN_AI_REQUEST_TEMPERATURE, it.doubleOrNull) }
        body["model"]?.jsonPrimitive?.let { span.setAttribute(GEN_AI_REQUEST_MODEL, it.content) }
        body["max_tokens"]?.jsonPrimitive?.intOrNull?.let { span.setAttribute(GEN_AI_REQUEST_MAX_TOKENS, it.toLong()) }

        // metadata
        body["metadata"]?.jsonObject?.let { metadata ->
            metadata["user_id"]?.jsonPrimitive?.let { span.setAttribute("gen_ai.metadata.user_id", it.content) }
        }
        body["service_tier"]?.jsonPrimitive?.let {
            span.setAttribute("gen_ai.usage.service_tier", it.content)
        }

        // system prompt
        body["system"]?.jsonObject?.let { system ->
            system["text"]?.jsonPrimitive?.let {
                span.setAttribute(
                    "gen_ai.prompt.system.content",
                    it.content.orRedactedInput()
                )
            }
            system["type"]?.jsonPrimitive?.let { span.setAttribute("gen_ai.prompt.system.type", it.content) }
        }

        body["top_k"]?.jsonPrimitive?.doubleOrNull?.let { span.setAttribute(GEN_AI_REQUEST_TOP_K, it) }
        body["top_p"]?.jsonPrimitive?.doubleOrNull?.let { span.setAttribute(GEN_AI_REQUEST_TOP_P, it) }

        body["messages"]?.let {
            if (it is JsonArray) {
                for ((index, message) in it.jsonArray.withIndex()) {
                    span.setAttribute("gen_ai.prompt.$index.role", message.jsonObject["role"]?.jsonPrimitive?.content)
                    val content = message.jsonObject["content"]?.toString()
                    // treat all request messages (including assistant history) as input per policy
                    span.setAttribute("gen_ai.prompt.$index.content", content?.orRedactedInput())
                }
            }
        }

        // extracting definitions of tool calls
        // see: https://docs.anthropic.com/en/api/messages#body-tools
        body["tools"]?.let {
            if (it is JsonArray) {
                for ((index, tool) in it.jsonArray.withIndex()) {
                    val name = tool.jsonObject["name"]?.jsonPrimitive?.contentOrNull
                    val description = tool.jsonObject["description"]?.jsonPrimitive?.contentOrNull
                    val type = tool.jsonObject["type"]?.jsonPrimitive?.contentOrNull
                    val parameters = tool.jsonObject["input_schema"]?.toString()

                    span.setAttribute("gen_ai.tool.$index.name", name?.orRedactedInput())
                    span.setAttribute("gen_ai.tool.$index.description", description?.orRedactedInput())
                    span.setAttribute("gen_ai.tool.$index.type", type)
                    span.setAttribute("gen_ai.tool.$index.parameters", parameters?.orRedactedInput())
                }
            }
        }

        if (contentTracingAllowed(ContentKind.INPUT)) {
            val mediaContent = parseMediaContent(body)
            if (mediaContent != null) {
                extractor.setUploadableContentAttributes(span, field = "input", mediaContent)
            }
        }

        span.populateUnmappedAttributes(body, mappedAttributes, PayloadType.REQUEST)
    }

    override fun getResponseBodyAttributes(span: Span, response: TracyHttpResponse) {
        val body = response.body.asJson()?.jsonObject ?: return

        body["id"]?.let { span.setAttribute(GEN_AI_RESPONSE_ID, it.jsonPrimitive.content) }
        body["type"]?.let { span.setAttribute(GEN_AI_OUTPUT_TYPE, it.jsonPrimitive.content) }
        body["role"]?.let { span.setAttribute("gen_ai.response.role", it.jsonPrimitive.content) }
        body["model"]?.let { span.setAttribute(GEN_AI_RESPONSE_MODEL, it.jsonPrimitive.content) }

        // collecting response messages
        body["content"]?.let {
            for ((index, message) in it.jsonArray.withIndex()) {
                val type = message.jsonObject["type"]?.jsonPrimitive?.content
                span.setAttribute("gen_ai.completion.$index.type", type)

                when (type) {
                    "text" -> {
                        // normal text message
                        span.setAttribute(
                            "gen_ai.completion.$index.content",
                            message.jsonObject["text"]?.toString()?.orRedactedOutput()
                        )
                    }

                    "tool_use" -> {
                        // tool call request by LLM
                        val toolCall = message
                        // gen_ai.tool.call.id
                        span.setAttribute(
                            "gen_ai.completion.$index.tool.call.id",
                            toolCall.jsonObject["id"]?.jsonPrimitive?.content
                        )
                        // gen_ai.tool.type
                        span.setAttribute(
                            "gen_ai.completion.$index.tool.call.type",
                            toolCall.jsonObject["type"]?.jsonPrimitive?.content
                        )
                        // gen_ai.tool.name
                        span.setAttribute(
                            "gen_ai.completion.$index.tool.name",
                            toolCall.jsonObject["name"]?.jsonPrimitive?.content?.orRedactedOutput()
                        )
                        span.setAttribute(
                            "gen_ai.completion.$index.tool.arguments",
                            toolCall.jsonObject["input"].toString().orRedactedOutput()
                        )
                    }

                    else -> {
                        span.setAttribute("gen_ai.completion.$index.content", message.toString().orRedactedOutput())
                    }
                }
            }
        }

        // finish reason
        body["stop_reason"]?.let {
            span.setAttribute(GEN_AI_RESPONSE_FINISH_REASONS, listOf(it.jsonPrimitive.content))
        }

        // collecting usage stats (e.g., input/output tokens)
        body["usage"]?.jsonObject?.let { usage ->
            usage["input_tokens"]?.jsonPrimitive?.intOrNull?.let {
                span.setAttribute(GEN_AI_USAGE_INPUT_TOKENS, it)
            }
            usage["output_tokens"]?.jsonPrimitive?.intOrNull?.let {
                span.setAttribute(GEN_AI_USAGE_OUTPUT_TOKENS, it)
            }
            usage["cache_creation_input_tokens"]?.jsonPrimitive?.intOrNull?.let {
                span.setAttribute("gen_ai.usage.cache_creation_input_tokens", it.toLong())
            }
            usage["cache_read_input_tokens"]?.jsonPrimitive?.intOrNull?.let {
                span.setAttribute("gen_ai.usage.cache_read_input_tokens", it.toLong())
            }
            usage["service_tier"]?.jsonPrimitive?.let {
                span.setAttribute("gen_ai.usage.service_tier", it.content)
            }
        }

        span.populateUnmappedAttributes(body, mappedAttributes, PayloadType.RESPONSE)
    }

    override fun getSpanName(request: TracyHttpRequest) = "Anthropic-generation"

    override fun isStreamingRequest(request: TracyHttpRequest): Boolean {
        val body = request.body.asJson()?.jsonObject ?: return false
        return body["stream"]?.jsonPrimitive?.booleanOrNull == true
    }

    override fun handleStreaming(span: Span, url: TracyHttpUrl, events: String): Unit = runCatching {
        val textBlocks = mutableMapOf<Int, StringBuilder>()
        val toolCallBlocks = mutableMapOf<Int, ToolCallAccumulator>()
        var role: String? = null
        var stopReason: String? = null

        for (line in events.lineSequence()) {
            if (!line.startsWith("data:")) continue
            val data = line.removePrefix("data:").trim()

            val event = runCatching {
                Json.parseToJsonElement(data).jsonObject
            }.getOrNull() ?: continue

            when (event["type"]?.jsonPrimitive?.content) {
                "message_start" -> {
                    val message = event["message"]?.jsonObject ?: continue
                    role = message["role"]?.jsonPrimitive?.content
                    message["usage"]?.jsonObject?.get("input_tokens")?.jsonPrimitive?.intOrNull?.let {
                        span.setAttribute(GEN_AI_USAGE_INPUT_TOKENS, it)
                    }
                }

                "content_block_start" -> {
                    val index = event["index"]?.jsonPrimitive?.intOrNull ?: continue
                    val block = event["content_block"]?.jsonObject ?: continue
                    when (block["type"]?.jsonPrimitive?.content) {
                        "text" -> textBlocks[index] = StringBuilder()
                        "tool_use" -> toolCallBlocks[index] = ToolCallAccumulator(
                            id = block["id"]?.jsonPrimitive?.content,
                            type = block["type"]?.jsonPrimitive?.content,
                            name = block["name"]?.jsonPrimitive?.content,
                        )
                    }
                }

                "content_block_delta" -> {
                    val index = event["index"]?.jsonPrimitive?.intOrNull ?: continue
                    val delta = event["delta"]?.jsonObject ?: continue
                    when (delta["type"]?.jsonPrimitive?.content) {
                        "text_delta" -> delta["text"]?.jsonPrimitive?.content?.let {
                            textBlocks.getOrPut(index) { StringBuilder() }.append(it)
                        }
                        "input_json_delta" -> delta["partial_json"]?.jsonPrimitive?.content?.let {
                            toolCallBlocks[index]?.arguments?.append(it)
                        }
                    }
                }

                "message_delta" -> {
                    event["delta"]?.jsonObject?.get("stop_reason")?.jsonPrimitive?.contentOrNull?.let {
                        stopReason = it
                    }
                    event["usage"]?.jsonObject?.get("output_tokens")?.jsonPrimitive?.intOrNull?.let {
                        span.setAttribute(GEN_AI_USAGE_OUTPUT_TOKENS, it)
                    }
                }
            }
        }

        role?.let { span.setAttribute("gen_ai.response.role", it) }
        stopReason?.let { span.setAttribute(GEN_AI_RESPONSE_FINISH_REASONS, listOf(it)) }

        // Set content block attributes — the same structure as non-streaming handler
        for (index in (textBlocks.keys + toolCallBlocks.keys).sorted()) {
            textBlocks[index]?.let {
                span.setAttribute("gen_ai.completion.$index.type", "text")
                span.setAttribute("gen_ai.completion.$index.content", it.toString().orRedactedOutput())
            }
            toolCallBlocks[index]?.let { tc ->
                span.setAttribute("gen_ai.completion.$index.type", "tool_use")
                tc.id?.let { span.setAttribute("gen_ai.completion.$index.tool.call.id", it) }
                tc.type?.let { span.setAttribute("gen_ai.completion.$index.tool.call.type", it) }
                tc.name?.let { span.setAttribute("gen_ai.completion.$index.tool.name", it.orRedactedOutput()) }
                if (tc.arguments.isNotEmpty()) {
                    span.setAttribute("gen_ai.completion.$index.tool.arguments", tc.arguments.toString().orRedactedOutput())
                }
            }
        }
    }.getOrElse { exception ->
        span.setStatus(StatusCode.ERROR)
        span.recordException(exception)
    }

    private class ToolCallAccumulator(
        val id: String? = null,
        val type: String? = null,
        val name: String? = null,
        val arguments: StringBuilder = StringBuilder(),
    )

    /**
     * Parses content of the `messages` field when its type is
     * either `ImageBlockParam` or `DocumentBlockParam`.
     *
     * The supported `source` fields are:
     *   1. Images (`ImageBlockParam`): `Base64ImageSource`, `URLImageSource`
     *   2. Documents (`DocumentBlockParam`): `Base64PDFSource`, `URLPDFSource`, `ContentBlockSource` with `ImageBlockParam`
     *
     * See [Messages API Docs](https://platform.claude.com/docs/en/api/messages/create)
     */
    private fun parseMediaContent(body: JsonObject): MediaContent? {
        if (body["messages"] !is JsonArray) {
            return null
        }

        val messages = body["messages"]?.jsonArray ?: return null

        val parts: List<MediaContentPart> = buildList {
            val supportedMessageTypes = listOf("image", "document")

            for (message in messages) {
                // message: { content: [] }
                if (message !is JsonObject || message["content"] !is JsonArray) {
                    continue
                }
                val content = message["content"]?.jsonArray ?: continue

                for (part in content) {
                    val messageType = part.jsonObject["type"]?.jsonPrimitive?.content ?: continue
                    if (messageType !in supportedMessageTypes) {
                        continue
                    }

                    // source is either of:
                    //  1. source: { data, media_type, type: "base64" }
                    //  2. source: { url, type: "url" }
                    //  3. source: { content: [{ type: "image", source: {...} }, ...] }
                    // see: https://platform.claude.com/docs/en/api/messages/create
                    val source = part.jsonObject["source"]?.jsonObject ?: continue
                    val contentParts = parseSource(messageType, source).map {
                        MediaContentPart(it)
                    }
                    addAll(contentParts)
                }
            }
        }

        return MediaContent(parts)
    }

    /**
     * Parses the `source` field of message types:
     *   1. `ImageBlockParam`: both `Base64ImageSource` and `URLImageSource`.
     *   2. `DocumentBlockParam`: `Base64PDFSource`, `URLPDFSource`, and `ContentBlockSource`.
     *
     * See [Messages API Docs](https://platform.claude.com/docs/en/api/messages/create)
     */
    private fun parseSource(messageType: String, source: JsonObject): List<Resource> {
        val sourceType = source["type"]?.jsonPrimitive?.content ?: return emptyList()
        val resources = when (sourceType) {
            "url" -> {
                val url = parseUrl(messageType, source) ?: return emptyList()
                listOf(url)
            }

            "base64" -> {
                val base64 = parseBase64(messageType, source) ?: return emptyList()
                listOf(base64)
            }

            "content" -> parseContent(messageType, source)
            else -> emptyList()
        }
        return resources
    }

    private fun parseUrl(messageType: String, source: JsonObject): Resource.Url? {
        val url = source["url"]?.jsonPrimitive?.content
        if (url == null) {
            logger.warn { "Message with type '$messageType' has no URL source" }
            return null
        }
        // add URL resource
        return Resource.Url(url)
    }

    private fun parseBase64(messageType: String, source: JsonObject): Resource.Base64? {
        val data = source["data"]?.jsonPrimitive?.content
        val mediaType = source["media_type"]?.jsonPrimitive?.content

        if (data == null || mediaType == null) {
            logger.warn { "Message with type '$messageType' misses either 'data' or 'media_type' attribute" }
            return null
        }

        // add base64 resource
        return Resource.Base64(data, mediaType)
    }

    private fun parseContent(messageType: String, source: JsonObject): List<Resource> {
        val content = source["content"]

        if (content == null || content !is JsonArray) {
            logger.warn { "Message with type '$messageType' has no content source" }
            return emptyList()
        }

        // content is an array of `ContentBlockSourceContent`.
        // See: https://platform.claude.com/docs/en/api/messages#content_block_source_content
        val resources: List<Resource> = buildList {
            for (param in content.jsonArray) {
                val type = param.jsonObject["type"]?.jsonPrimitive?.content ?: continue
                // ImageBlockParam
                if (type == "image") {
                    // the image is either Base64ImageSource or URLImageSource
                    val imageSource = param.jsonObject["source"]?.jsonObject ?: continue
                    val resource = parseSource(messageType, imageSource)
                    addAll(resource)
                }
            }
        }

        return resources
    }

    private val extractor: MediaContentExtractor = MediaContentExtractorImpl()

    // https://docs.claude.com/en/api/messages
    private val mappedRequestAttributes: List<String> = listOf(
        "temperature",
        "model",
        "max_tokens",
        "metadata",
        "service_tier",
        "system",
        "top_k",
        "top_p",
        "messages",
        "tools"
    )

    private val mappedResponseAttributes: List<String> = listOf(
        "id",
        "type",
        "role",
        "model",
        "content",
        "stop_reason",
        "usage"
    )

    private val mappedAttributes = mappedRequestAttributes + mappedResponseAttributes

    private val logger = KotlinLogging.logger {}
}