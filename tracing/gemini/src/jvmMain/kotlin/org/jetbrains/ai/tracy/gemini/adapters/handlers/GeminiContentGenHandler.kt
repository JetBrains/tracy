/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.gemini.adapters.handlers

import org.jetbrains.ai.tracy.core.adapters.LLMTracingAdapter.Companion.PayloadType
import org.jetbrains.ai.tracy.core.adapters.LLMTracingAdapter.Companion.populateUnmappedAttributes
import org.jetbrains.ai.tracy.core.adapters.handlers.EndpointApiHandler
import org.jetbrains.ai.tracy.core.adapters.media.MediaContent
import org.jetbrains.ai.tracy.core.adapters.media.MediaContentExtractor
import org.jetbrains.ai.tracy.core.adapters.media.MediaContentPart
import org.jetbrains.ai.tracy.core.adapters.media.Resource
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpRequest
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpResponse
import org.jetbrains.ai.tracy.core.http.protocol.asJson
import org.jetbrains.ai.tracy.core.policy.ContentKind
import org.jetbrains.ai.tracy.core.policy.contentTracingAllowed
import org.jetbrains.ai.tracy.core.policy.orRedactedInput
import org.jetbrains.ai.tracy.core.policy.orRedactedOutput
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.*
import kotlinx.serialization.json.*

/**
 * Parses Generate Content API requests and responses
 *
 * See [Generate Content API Docs](https://ai.google.dev/api/generate-content)
 */
class GeminiContentGenHandler(
    private val extractor: MediaContentExtractor
) : EndpointApiHandler {
    override fun handleRequestAttributes(span: Span, request: TracyHttpRequest) {
        // See: https://ai.google.dev/api/caching#Content
        val body = request.body.asJson()?.jsonObject ?: return

        body["contents"]?.let {
            for ((index, message) in it.jsonArray.withIndex()) {
                val role = message.jsonObject["role"]?.jsonPrimitive?.content
                span.setAttribute("gen_ai.prompt.$index.role", role)

                val parts = message.jsonObject["parts"]
                val textMessage = parts?.singleTextMessageInParts()

                if (textMessage != null) {
                    span.setAttribute("gen_ai.prompt.$index.content", textMessage.orRedactedInput())
                } else {
                    span.setAttribute("gen_ai.prompt.$index.content", parts?.toString()?.orRedactedInput())
                }
            }
        }

        // url ends with `[model]:[operation]`
        val (model, operation) = request.url.pathSegments.lastOrNull()?.split(":")
            ?.let { it.firstOrNull() to it.lastOrNull() } ?: (null to null)

        if (contentTracingAllowed(ContentKind.INPUT)) {
            val mediaContent = parseRequestMediaContent(body)
            if (mediaContent != null) {
                extractor.setUploadableContentAttributes(span, field = "input", mediaContent)
            }
        }

        model?.let { span.setAttribute(GEN_AI_REQUEST_MODEL, model) }
        operation?.let { span.setAttribute(GEN_AI_OPERATION_NAME, operation) }

        // extract tool calls
        body.jsonObject["tools"]?.let { tools ->
            if (tools is JsonArray) {
                for ((index, tool) in tools.jsonArray.withIndex()) {
                    tool.jsonObject["functionDeclarations"]?.let {
                        for ((functionIndex, function) in it.jsonArray.withIndex()) {
                            function.jsonObject["parameters"]?.jsonObject?.let { params ->
                                span.setAttribute(
                                    "gen_ai.tool.$index.function.$functionIndex.type",
                                    params["type"]?.jsonPrimitive?.content
                                )
                            }

                            val name = function.jsonObject["name"]?.jsonPrimitive?.contentOrNull
                            val description = function.jsonObject["description"]?.jsonPrimitive?.contentOrNull
                            val parameters = function.jsonObject["parameters"]?.toString()

                            span.setAttribute(
                                "gen_ai.tool.$index.function.$functionIndex.name",
                                name?.orRedactedInput(),
                            )
                            span.setAttribute(
                                "gen_ai.tool.$index.function.$functionIndex.description",
                                description?.orRedactedInput(),
                            )
                            span.setAttribute(
                                "gen_ai.tool.$index.function.$functionIndex.parameters",
                                parameters?.orRedactedInput(),
                            )
                        }
                    }
                }
            }
        }

        // See: https://ai.google.dev/api/generate-content#v1beta.GenerationConfig
        body["generationConfig"]?.let { config ->
            config.jsonObject["candidateCount"]?.jsonPrimitive?.intOrNull?.let {
                span.setAttribute(GEN_AI_REQUEST_CHOICE_COUNT, it.toLong())
            }
            config.jsonObject["maxOutputTokens"]?.jsonPrimitive?.intOrNull?.let {
                span.setAttribute(GEN_AI_REQUEST_MAX_TOKENS, it.toLong())
            }
            config.jsonObject["temperature"]?.jsonPrimitive?.doubleOrNull?.let {
                span.setAttribute(GEN_AI_REQUEST_TEMPERATURE, it)
            }
            config.jsonObject["topP"]?.jsonPrimitive?.doubleOrNull?.let { span.setAttribute(GEN_AI_REQUEST_TOP_P, it) }
            config.jsonObject["topK"]?.jsonPrimitive?.doubleOrNull?.let { span.setAttribute(GEN_AI_REQUEST_TOP_K, it) }
        }

        span.populateUnmappedAttributes(body, mappedAttributes, PayloadType.REQUEST)
    }

    override fun handleResponseAttributes(span: Span, response: TracyHttpResponse) {
        // See: https://ai.google.dev/api/generate-content#v1beta.GenerateContentResponse
        val body = response.body.asJson()?.jsonObject ?: return

        body["responseId"]?.let { span.setAttribute(GEN_AI_RESPONSE_ID, it.jsonPrimitive.content) }
        body["modelVersion"]?.let { span.setAttribute(GEN_AI_RESPONSE_MODEL, it.jsonPrimitive.content) }

        body["candidates"]?.let {
            for ((index, candidate) in it.jsonArray.withIndex()) {
                candidate.jsonObject["content"]?.let { content ->
                    val role = content.jsonObject["role"]?.jsonPrimitive?.content
                    val parts = content.jsonObject["parts"]

                    // Use singleTextMessageInParts() for smart text extraction in non-streaming responses
                    val textMessage = parts?.singleTextMessageInParts()
                    val text = textMessage ?: parts?.toString()

                    // Extract tool calls from parts
                    val toolCalls = buildList {
                        if (parts is JsonArray) {
                            for (part in parts.jsonArray) {
                                part.jsonObject["functionCall"]?.jsonObject?.let { call ->
                                    add(call["name"]?.jsonPrimitive?.content to call["args"]?.toString())
                                }
                            }
                        }
                    }

                    setCandidateAttributes(span, index, role, text, toolCalls)
                }

                span.setAttribute(
                    "gen_ai.completion.$index.finish_reason",
                    candidate.jsonObject["finishReason"]?.jsonPrimitive?.content
                )
            }
        }

        if (contentTracingAllowed(ContentKind.OUTPUT)) {
            val mediaContent = parseResponseMediaContent(body)
            if (mediaContent != null) {
                extractor.setUploadableContentAttributes(span, field = "output", mediaContent)
            }
        }

        body["usageMetadata"]?.jsonObject?.let { setUsageAttributes(span, it) }

        span.populateUnmappedAttributes(body, mappedAttributes, PayloadType.RESPONSE)
    }

    override fun handleStreaming(span: Span, events: String): Unit = runCatching {
        // Each SSE chunk is a complete GenerateContentResponse JSON.
        // Accumulate text per candidate index across all chunks and take
        // the last non-null values for metadata fields.
        data class CandidateAccumulator(
            val text: StringBuilder = StringBuilder(),
            var role: String? = null,
            var finishReason: String? = null,
            val toolCalls: MutableList<Pair<String?, String?>> = mutableListOf(), // (name, args)
        )

        val candidates = mutableMapOf<Int, CandidateAccumulator>()
        var responseId: String? = null
        var modelVersion: String? = null
        var usageMetadata: JsonObject? = null

        for (line in events.lineSequence()) {
            if (!line.startsWith("data:")) continue
            val data = line.removePrefix("data:").trim()

            val json = runCatching {
                Json.parseToJsonElement(data).jsonObject
            }.getOrNull() ?: continue

            json["responseId"]?.jsonPrimitive?.contentOrNull?.let { responseId = it }
            json["modelVersion"]?.jsonPrimitive?.contentOrNull?.let { modelVersion = it }
            json["usageMetadata"]?.jsonObject?.let { usageMetadata = it }

            json["candidates"]?.jsonArray?.forEachIndexed { index, candidate ->
                val acc = candidates.getOrPut(index) { CandidateAccumulator() }
                val content = candidate.jsonObject["content"]?.jsonObject

                content?.get("role")?.jsonPrimitive?.contentOrNull?.let { acc.role = it }

                content?.get("parts")?.jsonArray?.forEach { part ->
                    part.jsonObject["text"]?.jsonPrimitive?.contentOrNull?.let { acc.text.append(it) }

                    part.jsonObject["functionCall"]?.jsonObject?.let { call ->
                        val name = call["name"]?.jsonPrimitive?.contentOrNull
                        val args = call["args"]?.toString()
                        acc.toolCalls.add(name to args)
                    }
                }

                candidate.jsonObject["finishReason"]?.jsonPrimitive?.contentOrNull?.let {
                    acc.finishReason = it
                }
            }
        }

        // Set response-level attributes
        responseId?.let { span.setAttribute(GEN_AI_RESPONSE_ID, it) }
        modelVersion?.let { span.setAttribute(GEN_AI_RESPONSE_MODEL, it) }

        for ((index, acc) in candidates) {
            val text = acc.text.toString().ifEmpty { null }
            setCandidateAttributes(span, index, acc.role, text, acc.toolCalls)
            acc.finishReason?.let {
                span.setAttribute("gen_ai.completion.$index.finish_reason", it)
            }
        }

        usageMetadata?.let { setUsageAttributes(span, it) }

        return@runCatching
    }.getOrElse { exception ->
        span.setStatus(StatusCode.ERROR)
        span.recordException(exception)
    }

    private fun setCandidateAttributes(
        span: Span, index: Int, role: String?, text: String?,
        toolCalls: List<Pair<String?, String?>>,
    ) {
        role?.let { span.setAttribute("gen_ai.completion.$index.role", it) }
        text?.let { span.setAttribute("gen_ai.completion.$index.content", it.orRedactedOutput()) }
        for ((toolIndex, toolCall) in toolCalls.withIndex()) {
            val (name, args) = toolCall
            name?.let { span.setAttribute("gen_ai.completion.$index.tool.$toolIndex.name", it.orRedactedOutput()) }
            args?.let { span.setAttribute("gen_ai.completion.$index.tool.$toolIndex.arguments", it.orRedactedOutput()) }
        }
    }

    /**
     * Sets usage attributes from Gemini `usageMetadata`.
     *
     * Maps Gemini-specific field names to OpenTelemetry GenAI semantic conventions:
     * - `promptTokenCount` → `gen_ai.usage.input_tokens`
     * - `candidatesTokenCount` → `gen_ai.usage.output_tokens`
     * - `totalTokenCount` → `gen_ai.usage.total_tokens`
     * - `promptTokensDetails` / `candidatesTokensDetails` → per-modality token breakdowns
     *
     * See: [UsageMetadata](https://ai.google.dev/api/generate-content#UsageMetadata)
     */
    private fun setUsageAttributes(span: Span, usage: JsonObject) {
        usage["promptTokenCount"]?.jsonPrimitive?.intOrNull?.let {
            span.setAttribute(GEN_AI_USAGE_INPUT_TOKENS, it)
        }
        usage["candidatesTokenCount"]?.jsonPrimitive?.intOrNull?.let {
            span.setAttribute(GEN_AI_USAGE_OUTPUT_TOKENS, it)
        }
        usage["totalTokenCount"]?.jsonPrimitive?.intOrNull?.let {
            span.setAttribute("gen_ai.usage.total_tokens", it.toLong())
        }
        extractUsageTokenDetails(span, usage, attribute = "promptTokensDetails")
        extractUsageTokenDetails(span, usage, attribute = "candidatesTokensDetails")
    }

    private fun parseRequestMediaContent(body: JsonObject): MediaContent? {
        val contents = body["contents"]
        if (contents !is JsonArray) {
            return null
        }

        val resources: List<Resource> = buildList {
            for (content in contents.jsonArray) {
                val parts = content.jsonObject["parts"]
                if (parts !is JsonArray) {
                    continue
                }

                for (part in parts.jsonArray) {
                    val inlineData = part.jsonObject["inlineData"]?.jsonObject ?: continue
                    val resource = inlineData.toResource() ?: continue
                    add(resource)
                }
            }
        }

        return MediaContent(parts = resources.map { MediaContentPart(it) })
    }

    private fun parseResponseMediaContent(body: JsonObject): MediaContent? {
        val candidates = body["candidates"]
        if (candidates !is JsonArray) {
            return null
        }

        val resource: List<Resource> = buildList {
            for (candidate in candidates) {
                val content = candidate.jsonObject["content"]?.jsonObject ?: continue
                val parts = content["parts"]
                if (parts !is JsonArray) {
                    continue
                }

                for (part in parts.jsonArray) {
                    val inlineData = part.jsonObject["inlineData"]?.jsonObject ?: continue
                    val resource = inlineData.toResource() ?: continue
                    add(resource)
                }
            }
        }

        return MediaContent(parts = resource.map { MediaContentPart(it) })
    }

    /**
     * Should be executed on JSON objects that are of schema:
     * ```json
     * "inlineData": {
     *    "data": "...",
     *    "mimeType": "image/jpeg"
     * }
     * ```
     *
     * See the request body schema for the generateContent endpoint [here](https://ai.google.dev/api/generate-content?hl=en#request-body).
     * Next, navigate to contents [(Content)](https://ai.google.dev/api/caching?hl=en#Content)
     * then parts[] [(Part)](https://ai.google.dev/api/caching?hl=en#Part)
     * then inlineData [(Blob)](https://ai.google.dev/api/caching#Blob).
     *
     * Converts JSON objects matching the schema above into [Resource].
     */
    private fun JsonObject.toResource(): Resource? {
        val inlineData = this
        val data = inlineData["data"]?.jsonPrimitive?.content ?: return null
        val mimeType = inlineData["mimeType"]?.jsonPrimitive?.content ?: return null

        // NOTE: mediaType == mimeType when parameters are empty
        return Resource.Base64(data, mediaType = mimeType)
    }

    /**
     * Extracts `text` attribute from `parts` array if
     * `parts` contains only a single message with a single
     * `text` attribute.
     *
     * Examples:
     * 1. `text` will be returned:
     * ```json
     * {
     *     "parts": [
     *         {
     *             "text": "Hello! I am a large language model!"
     *         }
     *     ]
     * }
     * ```
     * 2. `null` will be returned (i.e., clients are expected to attach an entire `parts` array into span):
     * ```json
     * {
     *     "parts": [
     *         {
     *             "text": "Hello! I am a large language model.",
     *             "thoughtSignature": "CvcBAR/123"
     *         }
     *     ]
     * }
     * ```
     */
    private fun JsonElement.singleTextMessageInParts(): String? {
        val parts = this
        if (parts !is JsonArray || parts.size != 1) {
            return null
        }
        val item = parts.first().jsonObject
        // only the 'text' attribute is present -> display it on Langfuse with Markdown rendering
        if (item.keys.size == 1 && item.keys.first() == "text") {
            return item["text"]?.jsonPrimitive?.content
        }
        return null
    }

    private fun extractUsageTokenDetails(span: Span, usage: JsonElement, attribute: String) {
        // turn the given attribute into snake-cased format
        val snakeCasedAttribute = attribute.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()

        usage.jsonObject[attribute]?.let { usage ->
            for ((index, detail) in usage.jsonArray.withIndex()) {
                detail.jsonObject["modality"]?.let {
                    span.setAttribute("gen_ai.usage.$snakeCasedAttribute.$index.modality", it.jsonPrimitive.content)
                }
                detail.jsonObject["tokenCount"]?.jsonPrimitive?.intOrNull?.let {
                    span.setAttribute("gen_ai.usage.$snakeCasedAttribute.$index.token_count", it.toLong())
                }
            }
        }
    }

    private val mappedRequestAttributes: List<String> = listOf(
        "contents",
        "tools",
        "generationConfig"
    )

    private val mappedResponseAttributes: List<String> = listOf(
        "responseId",
        "modelVersion",
        "candidates",
        "usageMetadata"
    )

    private val mappedAttributes = mappedRequestAttributes + mappedResponseAttributes
}
