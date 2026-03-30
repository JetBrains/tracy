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
import org.jetbrains.ai.tracy.core.model.*
import org.jetbrains.ai.tracy.core.policy.ContentKind
import org.jetbrains.ai.tracy.core.policy.contentTracingAllowed
import org.jetbrains.ai.tracy.gemini.model.*
import io.opentelemetry.api.trace.Span
import kotlinx.serialization.json.*

/**
 * Parses Generate Content API requests and responses.
 *
 * Uses the 3-step tracing pipeline:
 * 1. **Parse**: JSON → [GenerateContentRequest]/[GenerateContentResponse]
 * 2. **Normalize**: Provider data classes → [TracedRequest]/[TracedResponse]
 * 3. **Serialize**: Unified model → span attributes via [SpanSerializer]
 *
 * See [Generate Content API Docs](https://ai.google.dev/api/generate-content)
 */
class GeminiContentGenHandler(
    private val extractor: MediaContentExtractor
) : EndpointApiHandler {

    private val json = Json { ignoreUnknownKeys = true }

    override fun handleRequestAttributes(span: Span, request: TracyHttpRequest) {
        val body = request.body.asJson()?.jsonObject ?: return

        // Step 1: Parse
        val parsed = json.decodeFromJsonElement<GenerateContentRequest>(body)

        // Extract model and operation from URL (Gemini-specific: url ends with `model:operation`)
        val (model, operation) = request.url.pathSegments.lastOrNull()?.split(":")
            ?.let { it.firstOrNull() to it.lastOrNull() } ?: (null to null)

        // Step 2: Normalize
        val traced = normalizeRequest(parsed, model, operation, body)

        // Step 3: Serialize
        SpanSerializer.writeRequest(span, traced, extractor)

        // Unmapped attributes (uses raw JsonObject — nothing is lost)
        span.populateUnmappedAttributes(body, mappedAttributes, PayloadType.REQUEST)
    }

    override fun handleResponseAttributes(span: Span, response: TracyHttpResponse) {
        val body = response.body.asJson()?.jsonObject ?: return

        // Step 1: Parse
        val parsed = json.decodeFromJsonElement<GenerateContentResponse>(body)

        // Step 2: Normalize
        val traced = normalizeResponse(parsed, body)

        // Step 3: Serialize
        SpanSerializer.writeResponse(span, traced, extractor)

        // Unmapped attributes
        span.populateUnmappedAttributes(body, mappedAttributes, PayloadType.RESPONSE)
    }

    override fun handleStreaming(span: Span, events: String) = Unit

    // --- Step 2: Normalization ---

    private fun normalizeRequest(
        req: GenerateContentRequest,
        model: String?,
        operation: String?,
        rawBody: JsonObject,
    ): TracedRequest {
        val messages = req.contents?.map { content ->
            TracedMessage(
                role = content.role,
                content = resolvePartsContent(content.parts),
                contentKind = ContentKind.INPUT,
            )
        } ?: emptyList()

        // Tool parsing uses raw JsonObject because the Gemini SDK serializes
        // function declarations with complex nested schemas (parametersJsonSchema, parameters)
        // that may not deserialize cleanly into typed data classes.
        val tools = parseTools(rawBody)

        val mediaContent = if (contentTracingAllowed(ContentKind.INPUT)) {
            parseRequestMediaContent(rawBody)
        } else null

        return TracedRequest(
            model = model,
            operationName = operation,
            temperature = req.generationConfig?.temperature,
            topP = req.generationConfig?.topP,
            topK = req.generationConfig?.topK,
            maxTokens = req.generationConfig?.maxOutputTokens?.toLong(),
            candidateCount = req.generationConfig?.candidateCount?.toLong(),
            messages = messages,
            tools = tools,
            mediaContent = mediaContent,
        )
    }

    private fun normalizeResponse(resp: GenerateContentResponse, rawBody: JsonObject): TracedResponse {
        val completions = resp.candidates?.map { candidate ->
            val parts = candidate.content?.parts
            val textContent = singleTextFromParts(parts)
            val toolCalls = extractToolCalls(parts)

            TracedCompletion(
                role = candidate.content?.role,
                content = textContent ?: if (toolCalls.isEmpty()) parts?.toString() else null,
                finishReason = candidate.finishReason,
                toolCalls = toolCalls,
            )
        } ?: emptyList()

        val usage = resp.usageMetadata?.let { meta ->
            val extras = buildMap<String, Any?> {
                meta.totalTokenCount?.let { put("gen_ai.usage.total_tokens", it.toLong()) }
                // Prompt tokens details
                meta.promptTokensDetails?.forEachIndexed { i, detail ->
                    detail.modality?.let { put("gen_ai.usage.prompt_tokens_details.$i.modality", it) }
                    detail.tokenCount?.let { put("gen_ai.usage.prompt_tokens_details.$i.token_count", it.toLong()) }
                }
                // Candidates tokens details
                meta.candidatesTokensDetails?.forEachIndexed { i, detail ->
                    detail.modality?.let { put("gen_ai.usage.candidates_tokens_details.$i.modality", it) }
                    detail.tokenCount?.let { put("gen_ai.usage.candidates_tokens_details.$i.token_count", it.toLong()) }
                }
            }
            TracedUsage(
                inputTokens = meta.promptTokenCount,
                outputTokens = meta.candidatesTokenCount,
                extraAttributes = extras,
            )
        }

        val mediaContent = if (contentTracingAllowed(ContentKind.OUTPUT)) {
            parseResponseMediaContent(rawBody)
        } else null

        return TracedResponse(
            id = resp.responseId,
            model = resp.modelVersion,
            completions = completions,
            usage = usage,
            mediaContent = mediaContent,
        )
    }

    // --- Tool parsing (raw JsonObject) ---

    /**
     * Parses tool definitions from raw JSON body.
     * Uses raw JsonObject navigation because the Gemini SDK serializes function declarations
     * with fields like `parametersJsonSchema` that require careful extraction.
     */
    private fun parseTools(body: JsonObject): List<TracedTool> {
        val tools = body["tools"]
        if (tools !is JsonArray) return emptyList()

        return tools.jsonArray.flatMap { tool ->
            val fns = tool.jsonObject["functionDeclarations"]?.jsonArray
                ?: return@flatMap emptyList()
            fns.map { fn ->
                TracedTool(
                    type = "function",
                    name = fn.jsonObject["name"]?.jsonPrimitive?.contentOrNull,
                    description = fn.jsonObject["description"]?.jsonPrimitive?.contentOrNull,
                    parameters = (fn.jsonObject["parameters"]
                        ?: fn.jsonObject["parametersJsonSchema"])?.toString(),
                )
            }
        }
    }

    // --- Helpers ---

    /**
     * Extracts text from `parts` array. If `parts` contains only a single text entry,
     * returns just the text. Otherwise returns the full parts array as string.
     */
    private fun resolvePartsContent(parts: List<JsonElement>?): String? {
        if (parts == null) return null
        val text = singleTextFromParts(parts)
        return text ?: parts.toString()
    }

    /**
     * Returns the text value if parts contains exactly one text part.
     */
    private fun singleTextFromParts(parts: List<JsonElement>?): String? {
        if (parts == null || parts.size != 1) return null
        val item = parts.first()
        if (item !is JsonObject) return null
        if (item.keys.size == 1 && "text" in item.keys) return item["text"]?.jsonPrimitive?.content
        return null
    }

    /**
     * Extracts function call tool calls from parts array.
     */
    private fun extractToolCalls(parts: List<JsonElement>?): List<TracedToolCall> {
        if (parts == null) return emptyList()
        var callIndex = 0
        return buildList {
            for (part in parts) {
                if (part !is JsonObject) continue
                val functionCall = part["functionCall"]?.jsonObject ?: continue
                val name = functionCall["name"]?.jsonPrimitive?.content
                add(
                    TracedToolCall(
                        id = "call_${name ?: callIndex}", // Gemini doesn't provide tool call IDs
                        type = "function",
                        name = name,
                        arguments = functionCall["args"]?.toString(),
                    )
                )
                callIndex++
            }
        }
    }

    private fun parseRequestMediaContent(body: JsonObject): MediaContent? {
        val contents = body["contents"]
        if (contents !is JsonArray) return null

        val resources = buildList<Resource> {
            for (content in contents.jsonArray) {
                val parts = content.jsonObject["parts"]
                if (parts !is JsonArray) continue
                for (part in parts.jsonArray) {
                    val inlineData = part.jsonObject["inlineData"]?.jsonObject ?: continue
                    val resource = inlineData.toResource() ?: continue
                    add(resource)
                }
            }
        }

        return if (resources.isNotEmpty()) MediaContent(resources.map { MediaContentPart(it) }) else null
    }

    private fun parseResponseMediaContent(body: JsonObject): MediaContent? {
        val candidates = body["candidates"]
        if (candidates !is JsonArray) return null

        val resources = buildList<Resource> {
            for (candidate in candidates) {
                val content = candidate.jsonObject["content"]?.jsonObject ?: continue
                val parts = content["parts"]
                if (parts !is JsonArray) continue
                for (part in parts.jsonArray) {
                    val inlineData = part.jsonObject["inlineData"]?.jsonObject ?: continue
                    val resource = inlineData.toResource() ?: continue
                    add(resource)
                }
            }
        }

        return if (resources.isNotEmpty()) MediaContent(resources.map { MediaContentPart(it) }) else null
    }

    private fun JsonObject.toResource(): Resource? {
        val data = this["data"]?.jsonPrimitive?.content ?: return null
        val mimeType = this["mimeType"]?.jsonPrimitive?.content ?: return null
        return Resource.Base64(data, mediaType = mimeType)
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
