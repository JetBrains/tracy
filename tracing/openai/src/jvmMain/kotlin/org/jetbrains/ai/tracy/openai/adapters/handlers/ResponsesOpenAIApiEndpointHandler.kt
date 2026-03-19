/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.openai.adapters.handlers

import org.jetbrains.ai.tracy.core.adapters.LLMTracingAdapter.Companion.PayloadType
import org.jetbrains.ai.tracy.core.adapters.LLMTracingAdapter.Companion.populateUnmappedAttributes
import org.jetbrains.ai.tracy.core.adapters.handlers.EndpointApiHandler
import org.jetbrains.ai.tracy.core.adapters.media.*
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpRequest
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpResponse
import org.jetbrains.ai.tracy.core.http.protocol.asJson
import org.jetbrains.ai.tracy.core.model.*
import org.jetbrains.ai.tracy.core.policy.ContentKind
import org.jetbrains.ai.tracy.core.policy.contentTracingAllowed
import org.jetbrains.ai.tracy.core.policy.orRedactedInput
import org.jetbrains.ai.tracy.core.policy.orRedactedOutput
import org.jetbrains.ai.tracy.openai.model.*
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_OUTPUT_TYPE
import kotlinx.serialization.json.*

/**
 * Handler for OpenAI Responses API.
 *
 * Uses the 3-step tracing pipeline:
 * 1. **Parse**: JSON → [ResponsesRequest]/[ResponsesResponse]
 * 2. **Normalize**: Provider data classes → [TracedRequest]/[TracedResponse]
 * 3. **Serialize**: Unified model → span attributes via [SpanSerializer]
 */
internal class ResponsesOpenAIApiEndpointHandler(
    private val extractor: MediaContentExtractor
) : EndpointApiHandler {

    private val json = Json { ignoreUnknownKeys = true }

    override fun handleRequestAttributes(span: Span, request: TracyHttpRequest) {
        val body = request.body.asJson()?.jsonObject ?: return

        // Step 1: Parse
        val parsed = json.decodeFromJsonElement<ResponsesRequest>(body)

        // Step 2: Normalize
        val traced = normalizeRequest(parsed)

        // Step 3: Serialize
        SpanSerializer.writeRequest(span, traced, extractor)

        // Unmapped attributes (uses raw JsonObject — nothing is lost)
        span.populateUnmappedAttributes(body, mappedAttributes, PayloadType.REQUEST)
    }

    override fun handleResponseAttributes(span: Span, response: TracyHttpResponse) {
        val body = response.body.asJson()?.jsonObject ?: return

        // Step 1: Parse
        val parsed = json.decodeFromJsonElement<ResponsesResponse>(body)

        // Step 2: Normalize
        val traced = normalizeResponse(parsed)

        // Step 3: Serialize
        SpanSerializer.writeResponse(span, traced, extractor)

        // Unmapped attributes
        span.populateUnmappedAttributes(body, mappedAttributes, PayloadType.RESPONSE)
    }

    override fun handleStreaming(span: Span, events: String): Unit = runCatching {
        for (line in events.lineSequence()) {
            if (!line.startsWith("data:")) continue
            val data = line.removePrefix("data:").trim()

            val event = runCatching {
                json.decodeFromString<ResponsesStreamEvent>(data)
            }.getOrNull() ?: continue

            if (event.type == "response.output_text.done") {
                event.text?.let {
                    span.setAttribute("gen_ai.completion.0.content", it.orRedactedOutput())
                    span.setAttribute("gen_ai.completion.0.finish_reason", "stop")
                }
            }
        }
    }.getOrElse { exception ->
        span.setStatus(StatusCode.ERROR)
        span.recordException(exception)
    }

    // --- Step 2: Normalization ---

    private fun normalizeRequest(req: ResponsesRequest): TracedRequest {
        val systemPrompt = req.instructions?.let { TracedSystemPrompt.Text(it) }

        val messages = mutableListOf<TracedMessage>()
        var mediaContent: MediaContent? = null

        when (val input = req.input) {
            is JsonPrimitive -> {
                messages.add(
                    TracedMessage(role = "user", content = input.contentOrNull, contentKind = ContentKind.INPUT)
                )
            }

            is JsonArray -> {
                messages.addAll(normalizeInputItems(input))
                mediaContent = extractMediaFromInputItems(input)
            }

            else -> {} // null or other
        }

        val tools = req.tools?.map { tool ->
            TracedTool(
                type = tool.type,
                name = tool.name,
                description = tool.description,
                parameters = tool.parameters?.toString(),
                strict = tool.strict?.toString(),
            )
        } ?: emptyList()

        val extras = buildMap<String, Any?> {
            req.previousResponseId?.let { put("gen_ai.request.previous_response_id", it) }
            req.store?.let { put("gen_ai.request.store", it) }
            req.truncation?.let { put("gen_ai.request.truncation", it) }
            req.parallelToolCalls?.let { put("gen_ai.request.parallel_tool_calls", it) }
            req.stream?.let { put("gen_ai.request.stream", it) }
            req.responseFormat?.let {
                val value = when (it) {
                    is JsonPrimitive -> it.contentOrNull
                    else -> it.toString()
                }
                put(GEN_AI_OUTPUT_TYPE.key, value)
            }
            req.toolChoice?.let {
                val value = when (it) {
                    is JsonPrimitive -> it.content
                    else -> it.toString()
                }
                put("gen_ai.request.tool_choice", value)
            }
            req.reasoning?.let { put("gen_ai.request.reasoning", it.toString()) }
            req.text?.let { put("gen_ai.request.text", it.toString()) }
        }

        return TracedRequest(
            model = req.model,
            temperature = req.temperature,
            topP = req.topP,
            maxTokens = req.maxOutputTokens,
            systemPrompt = systemPrompt,
            messages = messages,
            tools = tools,
            mediaContent = mediaContent,
            extraAttributes = extras,
        )
    }

    private fun normalizeResponse(resp: ResponsesResponse): TracedResponse {
        val completions = resp.output.mapNotNull { outputItem ->
            val obj = (outputItem as? JsonObject) ?: return@mapNotNull null
            when (obj["type"]?.jsonPrimitive?.contentOrNull) {
                "message", null -> normalizeMessageOutput(obj)
                "function_call" -> normalizeFunctionCallOutput(obj)
                "reasoning" -> normalizeReasoningOutput(obj)
                else -> null
            }
        }

        return TracedResponse(
            id = resp.id,
            model = resp.model,
            operationName = resp.objectType,
            completions = completions,
            usage = resp.usage?.let {
                TracedUsage(inputTokens = it.inputTokens, outputTokens = it.outputTokens)
            },
        )
    }

    // --- Input item normalization ---

    private fun normalizeInputItems(inputs: JsonArray): List<TracedMessage> {
        return inputs.mapNotNull { input ->
            val obj = (input as? JsonObject) ?: return@mapNotNull null
            when (obj["type"]?.jsonPrimitive?.contentOrNull) {
                "message" -> normalizeMessageInput(obj)
                "function_call" -> normalizeFunctionCallInput(obj)
                "function_call_output" -> normalizeFunctionCallOutputInput(obj)
                "reasoning" -> normalizeReasoningInput(obj)
                else -> null
            }
        }
    }

    private fun normalizeMessageInput(item: JsonObject): TracedMessage {
        val role = item["role"]?.jsonPrimitive?.contentOrNull
        val content = resolveInputContent(item["content"])
        val kind = kindByRole(role)
        return TracedMessage(role = role, content = content, contentKind = kind)
    }

    private fun normalizeFunctionCallInput(item: JsonObject): TracedMessage {
        val callId = item["call_id"]?.jsonPrimitive?.contentOrNull
        val name = item["name"]?.jsonPrimitive?.contentOrNull
        val arguments = item["arguments"]?.jsonPrimitive?.contentOrNull
        return TracedMessage(
            role = "assistant",
            contentKind = ContentKind.OUTPUT,
            extraAttributes = buildMap {
                callId?.let { put("tool.0.call.id", it) }
                put("tool.0.call.type", "function_call")
                name?.let { put("tool.0.name", it.orRedactedInput()) }
                arguments?.let { put("tool.0.arguments", it.orRedactedInput()) }
            },
        )
    }

    private fun normalizeFunctionCallOutputInput(item: JsonObject): TracedMessage {
        val callId = item["call_id"]?.jsonPrimitive?.contentOrNull
        val output = item["output"]?.jsonPrimitive?.contentOrNull
        return TracedMessage(
            role = "tool",
            content = output,
            contentKind = ContentKind.INPUT,
            toolCallId = callId,
        )
    }

    private fun normalizeReasoningInput(item: JsonObject): TracedMessage {
        val content = item["content"]?.toString()
        return TracedMessage(
            role = "assistant",
            content = content,
            contentKind = ContentKind.OUTPUT,
            extraAttributes = mapOf("type" to "reasoning"),
        )
    }

    // --- Output item normalization ---

    private fun normalizeMessageOutput(item: JsonObject): TracedCompletion {
        val role = item["role"]?.jsonPrimitive?.contentOrNull
        val status = item["status"]?.jsonPrimitive?.contentOrNull
        val (text, annotations) = resolveOutputContent(item["content"])

        return TracedCompletion(
            role = role,
            content = text,
            finishReason = status,
            annotations = annotations,
        )
    }

    private fun normalizeFunctionCallOutput(item: JsonObject): TracedCompletion {
        val callId = item["call_id"]?.jsonPrimitive?.contentOrNull
        val name = item["name"]?.jsonPrimitive?.contentOrNull
        val arguments = item["arguments"]?.jsonPrimitive?.contentOrNull
        val status = item["status"]?.jsonPrimitive?.contentOrNull

        return TracedCompletion(
            finishReason = status,
            toolCalls = listOf(
                TracedToolCall(
                    id = callId,
                    type = "function_call",
                    name = name,
                    arguments = arguments,
                )
            ),
        )
    }

    private fun normalizeReasoningOutput(item: JsonObject): TracedCompletion {
        val content = item["content"]?.toString()

        return TracedCompletion(
            type = "reasoning",
            content = content,
        )
    }

    // --- Helpers ---

    /**
     * Resolves polymorphic input message content.
     * If content is a JsonArray with a single `input_text` element, extracts the `text` field.
     * Otherwise, serializes the whole element.
     */
    private fun resolveInputContent(content: JsonElement?): String? = when (content) {
        null, is JsonNull -> null
        is JsonPrimitive -> content.contentOrNull
        is JsonArray -> {
            if (content.size == 1 && content.first().jsonObject["type"]?.jsonPrimitive?.contentOrNull == "input_text") {
                content.first().jsonObject["text"]?.jsonPrimitive?.content
            } else {
                content.toString()
            }
        }

        else -> content.toString()
    }

    private data class OutputContent(val text: String?, val annotations: String?)

    /**
     * Resolves polymorphic output message content.
     * If content is a JsonArray with a single `output_text` element, extracts `text` and `annotations`.
     * Otherwise, serializes the whole element.
     */
    private fun resolveOutputContent(content: JsonElement?): OutputContent = when (content) {
        null, is JsonNull -> OutputContent(null, null)
        is JsonArray -> {
            if (content.size == 1 && content.first().jsonObject["type"]?.jsonPrimitive?.contentOrNull == "output_text") {
                val message = content.first().jsonObject
                OutputContent(
                    text = message["text"]?.jsonPrimitive?.content,
                    annotations = message["annotations"]?.toString(),
                )
            } else {
                OutputContent(text = content.toString(), annotations = null)
            }
        }

        else -> OutputContent(text = content.toString(), annotations = null)
    }

    /**
     * Given a role, define what content kind matches it, either input or output.
     */
    private fun kindByRole(role: String?): ContentKind = when (role) {
        "developer", "system", "user" -> ContentKind.INPUT
        else -> ContentKind.OUTPUT
    }

    /**
     * Extracts media content from input items that have content arrays.
     */
    private fun extractMediaFromInputItems(inputs: JsonArray): MediaContent? {
        if (!contentTracingAllowed(ContentKind.INPUT)) return null

        val allParts = mutableListOf<MediaContentPart>()
        for (input in inputs) {
            val content = input.jsonObject["content"]
            if (content is JsonArray) {
                allParts.addAll(parseMediaContent(content).parts)
            }
        }
        return if (allParts.isNotEmpty()) MediaContent(allParts) else null
    }

    /**
     * Extracts media content parts (images, files) from JSON content.
     *
     * See details: [Responses API](https://platform.openai.com/docs/api-reference/responses/create)
     */
    private fun parseMediaContent(content: JsonArray): MediaContent {
        val parts = buildList {
            for (part in content) {
                val type = part.jsonObject["type"]?.jsonPrimitive?.content ?: continue

                val mediaPart = when (type) {
                    "input_image" -> {
                        val url = part.jsonObject["image_url"]?.jsonPrimitive?.content ?: continue
                        when {
                            url.isValidUrl() -> MediaContentPart(Resource.Url(url))
                            url.startsWith("data:") -> MediaContentPart(Resource.InlineDataUrl(url))
                            else -> null
                        }
                    }

                    "input_file" -> when {
                        "file_url" in part.jsonObject -> {
                            val url = part.jsonObject["file_url"]?.jsonPrimitive?.content ?: continue
                            if (url.isValidUrl()) MediaContentPart(Resource.Url(url)) else null
                        }

                        "file_data" in part.jsonObject -> {
                            val dataUrl = part.jsonObject["file_data"]?.jsonPrimitive?.content ?: continue
                            MediaContentPart(Resource.InlineDataUrl(dataUrl))
                        }

                        else -> null
                    }

                    else -> null
                }

                // if the media part is valid, append it to the list
                if (mediaPart != null) {
                    add(mediaPart)
                }
            }
        }

        return MediaContent(parts)
    }

    // https://platform.openai.com/docs/api-reference/responses/create
    private val mappedRequestAttributes: List<String> = listOf(
        "temperature",
        "model",
        "previous_response_id",
        "store",
        "top_p",
        "max_output_tokens",
        "truncation",
        "parallel_tool_calls",
        "stream",
        "response_format",
        "tool_choice",
        "reasoning",
        "text",
        "input",
        "instructions",
        "tools",
    )

    // https://platform.openai.com/docs/api-reference/responses/object
    private val mappedResponseAttributes: List<String> = listOf(
        "id",
        "object",
        "model",
        "output",
        "usage",
    )

    private val mappedAttributes = mappedRequestAttributes + mappedResponseAttributes
}
