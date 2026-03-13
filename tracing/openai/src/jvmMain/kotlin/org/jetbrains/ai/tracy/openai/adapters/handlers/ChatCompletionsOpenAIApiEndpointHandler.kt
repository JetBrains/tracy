/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.openai.adapters.handlers

import org.jetbrains.ai.tracy.core.adapters.LLMTracingAdapter.Companion.PayloadType
import org.jetbrains.ai.tracy.core.adapters.LLMTracingAdapter.Companion.populateUnmappedAttributes
import org.jetbrains.ai.tracy.core.adapters.handlers.EndpointApiHandler
import org.jetbrains.ai.tracy.core.adapters.media.MediaContent
import org.jetbrains.ai.tracy.core.adapters.media.MediaContentExtractor
import org.jetbrains.ai.tracy.core.adapters.media.MediaContentPart
import org.jetbrains.ai.tracy.core.adapters.media.Resource
import org.jetbrains.ai.tracy.core.adapters.media.isValidUrl
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpRequest
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpResponse
import org.jetbrains.ai.tracy.core.http.protocol.asJson
import org.jetbrains.ai.tracy.core.policy.ContentKind
import org.jetbrains.ai.tracy.core.policy.contentTracingAllowed
import org.jetbrains.ai.tracy.core.policy.orRedacted
import org.jetbrains.ai.tracy.core.policy.orRedactedInput
import org.jetbrains.ai.tracy.core.policy.orRedactedOutput
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_RESPONSE_ID
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_RESPONSE_MODEL
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_USAGE_INPUT_TOKENS
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_USAGE_OUTPUT_TOKENS
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive


/**
 * Handler for OpenAI Chat Completions API
 */
internal class ChatCompletionsOpenAIApiEndpointHandler(
    private val extractor: MediaContentExtractor
) : EndpointApiHandler {
    override fun handleRequestAttributes(span: Span, request: TracyHttpRequest) {
        val body = request.body.asJson()?.jsonObject ?: return
        OpenAIApiUtils.setCommonRequestAttributes(span, request)

        body["messages"]?.let {
            for ((index, message) in it.jsonArray.withIndex()) {
                val role = message.jsonObject["role"]?.jsonPrimitive?.content
                val kind = kindByRole(role)

                span.setAttribute("gen_ai.prompt.$index.role", role)

                // content may be of different schemas
                val messageContent = message.jsonObject["content"]
                attachRequestContent(span, index, kind, messageContent)

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
                    val toolType = tool.jsonObject["type"]?.jsonPrimitive?.content
                    span.setAttribute("gen_ai.tool.$index.type", toolType)

                    tool.jsonObject["function"]?.jsonObject?.let {
                        val toolName = it["name"]?.jsonPrimitive?.content
                        val toolDescription = it["description"]?.jsonPrimitive?.content
                        val toolParameters = it["parameters"]?.jsonObject?.toString()
                        val strict = it["strict"]?.jsonPrimitive?.boolean?.toString()

                        span.setAttribute("gen_ai.tool.$index.name", toolName?.orRedactedInput())
                        span.setAttribute("gen_ai.tool.$index.description", toolDescription?.orRedactedInput())
                        span.setAttribute("gen_ai.tool.$index.parameters", toolParameters?.orRedactedInput())
                        span.setAttribute("gen_ai.tool.$index.strict", strict)
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
        kind: ContentKind,
        content: JsonElement?,
    ) {
        if (content == null) {
            span.setAttribute("gen_ai.prompt.$index.content", null)
            return
        }

        // See content types: https://platform.openai.com/docs/api-reference/chat/create#chat-create-messages-user-message-content
        val result: String = when (content) {
            is JsonPrimitive -> content.jsonPrimitive.content
            is JsonArray -> {
                // install upload media attributes only when tracing is allowed
                if (contentTracingAllowed(kind)) {
                    // array that contains entries of either image, audio, file or normal text
                    val mediaContent = parseMediaContent(content)
                    extractor.setUploadableContentAttributes(span, field = "input", mediaContent)
                }
                content.jsonArray.toString()
            }

            else -> content.toString()
        }
        span.setAttribute("gen_ai.prompt.$index.content", result.orRedacted(kind))
    }

    override fun handleResponseAttributes(span: Span, response: TracyHttpResponse) {
        val body = response.body.asJson()?.jsonObject ?: return

        body["choices"]?.let { choices ->
            for ((idx, choice) in choices.jsonArray.withIndex()) {
                val index = choice.jsonObject["index"]?.jsonPrimitive?.intOrNull ?: idx
                val finishReason = choice.jsonObject["finish_reason"]?.jsonPrimitive?.content
                val message = choice.jsonObject["message"]?.jsonObject

                val role = message?.get("role")?.jsonPrimitive?.content
                val content = message?.get("content")?.toString()
                val annotations = message?.get("annotations")?.toString()

                // Parse tool calls from the response message
                val toolCalls = buildMap<Int, ToolCallData> {
                    val toolCallsJson = message?.get("tool_calls")
                    // sometimes, this prop is explicitly set to null, hence, being JsonNull.
                    // therefore, we check for the required array type
                    if (toolCallsJson is JsonArray) {
                        for ((tcIndex, tc) in toolCallsJson.jsonArray.withIndex()) {
                            val fn = tc.jsonObject["function"]?.jsonObject
                            put(tcIndex, ToolCallData(
                                id = tc.jsonObject["id"]?.jsonPrimitive?.content,
                                type = tc.jsonObject["type"]?.jsonPrimitive?.content,
                                name = fn?.get("name")?.jsonPrimitive?.content,
                                arguments = fn?.get("arguments")?.jsonPrimitive?.content,
                            ))
                        }
                    }
                }

                setChoiceAttributes(span, index, role, content, finishReason, toolCalls, annotations)
            }
        }

        body["usage"]?.let { usage ->
            setUsageAttributes(span, usage.jsonObject)
        }

        span.populateUnmappedAttributes(body, mappedAttributes, PayloadType.RESPONSE)
    }

    override fun handleStreaming(span: Span, events: String): Unit = runCatching {
        data class ToolCallAccumulator(
            var id: String? = null,
            var type: String? = null,
            var name: String? = null,
            val arguments: StringBuilder = StringBuilder(),
        )

        data class ChoiceAccumulator(
            val content: StringBuilder = StringBuilder(),
            var role: String? = null,
            var finishReason: String? = null,
            val toolCalls: MutableMap<Int, ToolCallAccumulator> = mutableMapOf(),
        )

        val choices = mutableMapOf<Int, ChoiceAccumulator>()
        var responseId: String? = null
        var responseModel: String? = null

        for (line in events.lineSequence()) {
            if (!line.startsWith("data:")) continue
            val data = line.removePrefix("data:").trim()

            val json = runCatching {
                Json.parseToJsonElement(data).jsonObject
            }.getOrNull() ?: continue

            // Response-level attributes (same across all chunks)
            if (responseId == null) {
                json["id"]?.jsonPrimitive?.contentOrNull?.let { responseId = it }
            }
            if (responseModel == null) {
                json["model"]?.jsonPrimitive?.contentOrNull?.let { responseModel = it }
            }

            // Usage chunk (sent when stream_options.include_usage is true):
            // the final chunk has choices=[] and usage={...}
            json["usage"]?.jsonObject?.let { usage ->
                setUsageAttributes(span, usage)
            }

            val eventChoices = json["choices"]?.jsonArray ?: continue
            for (choiceElement in eventChoices) {
                val choice = choiceElement.jsonObject
                val index = choice["index"]?.jsonPrimitive?.intOrNull ?: 0
                val acc = choices.getOrPut(index) { ChoiceAccumulator() }

                choice["finish_reason"]?.jsonPrimitive?.contentOrNull?.let {
                    acc.finishReason = it
                }

                val delta = choice["delta"]?.jsonObject ?: continue

                delta["role"]?.jsonPrimitive?.contentOrNull?.let { acc.role = it }
                delta["content"]?.jsonPrimitive?.contentOrNull?.let { acc.content.append(it) }

                // Tool calls in delta: each tool_call has index, id, type, function.name, function.arguments
                delta["tool_calls"]?.jsonArray?.forEach { toolCallElement ->
                    val tc = toolCallElement.jsonObject
                    val tcIndex = tc["index"]?.jsonPrimitive?.intOrNull ?: return@forEach
                    val tcAcc = acc.toolCalls.getOrPut(tcIndex) { ToolCallAccumulator() }

                    tc["id"]?.jsonPrimitive?.contentOrNull?.let { tcAcc.id = it }
                    tc["type"]?.jsonPrimitive?.contentOrNull?.let { tcAcc.type = it }
                    tc["function"]?.jsonObject?.let { fn ->
                        fn["name"]?.jsonPrimitive?.contentOrNull?.let { tcAcc.name = it }
                        fn["arguments"]?.jsonPrimitive?.contentOrNull?.let { tcAcc.arguments.append(it) }
                    }
                }
            }
        }

        // Set response-level attributes
        responseId?.let { span.setAttribute(GEN_AI_RESPONSE_ID, it) }
        responseModel?.let { span.setAttribute(GEN_AI_RESPONSE_MODEL, it) }

        for ((index, acc) in choices) {
            val toolCallData = acc.toolCalls.mapValues { (_, tc) ->
                ToolCallData(id = tc.id, type = tc.type, name = tc.name, arguments = tc.arguments.toString().ifEmpty { null })
            }
            setChoiceAttributes(
                span, index, acc.role, acc.content.toString().ifEmpty { null },
                acc.finishReason, toolCallData,
            )
        }

        return@runCatching
    }.getOrElse { exception ->
        span.setStatus(StatusCode.ERROR)
        span.recordException(exception)
    }

    private data class ToolCallData(
        val id: String? = null,
        val type: String? = null,
        val name: String? = null,
        val arguments: String? = null,
    )

    private fun setChoiceAttributes(
        span: Span, index: Int, role: String?, content: String?,
        finishReason: String?, toolCalls: Map<Int, ToolCallData>,
        annotations: String? = null,
    ) {
        val kind = kindByRole(role)
        role?.let { span.setAttribute("gen_ai.completion.$index.role", it) }
        finishReason?.let { span.setAttribute("gen_ai.completion.$index.finish_reason", it) }
        content?.let { span.setAttribute("gen_ai.completion.$index.content", it.orRedacted(kind)) }
        for ((tcIndex, tc) in toolCalls) {
            tc.id?.let { span.setAttribute("gen_ai.completion.$index.tool.$tcIndex.call.id", it) }
            tc.type?.let { span.setAttribute("gen_ai.completion.$index.tool.$tcIndex.call.type", it) }
            tc.name?.let { span.setAttribute("gen_ai.completion.$index.tool.$tcIndex.name", it.orRedactedOutput()) }
            tc.arguments?.let { span.setAttribute("gen_ai.completion.$index.tool.$tcIndex.arguments", it.orRedactedOutput()) }
        }
        annotations?.let { span.setAttribute("gen_ai.completion.$index.annotations", it) }
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
     * Given a role, define what content kind matches it, either input or output.
     */
    private fun kindByRole(role: String?): ContentKind = when (role) {
        // role may be:
        //   1. input: developer/system/user
        "developer", "system", "user" -> ContentKind.INPUT
        //   2. output: assistant/tool/function
        else -> ContentKind.OUTPUT
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
                            MediaContentPart(resource = Resource.Url(url))
                        } else if (url.startsWith("data:")) {
                            MediaContentPart(resource = Resource.InlineDataUrl(url))
                        } else {
                            null
                        }
                    }

                    "input_audio" -> {
                        // data is base64-encoded
                        val data = part.jsonObject["input_audio"]?.jsonObject["data"]?.jsonPrimitive?.content
                            ?: continue
                        val format = part.jsonObject["input_audio"]?.jsonObject["format"]?.jsonPrimitive?.content
                            ?: continue

                        MediaContentPart(resource = Resource.Base64(data, mediaType = "audio/$format"))
                    }

                    "file" -> {
                        // OpenAI expects a data url with a base64-encoded PDF file
                        val fileData = part.jsonObject["file"]?.jsonObject["file_data"]?.jsonPrimitive?.content
                            ?: continue
                        MediaContentPart(resource = Resource.InlineDataUrl(fileData))
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
}
