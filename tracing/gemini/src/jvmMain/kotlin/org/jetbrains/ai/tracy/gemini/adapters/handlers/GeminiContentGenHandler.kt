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
                    span.setAttribute(
                        "gen_ai.completion.$index.role",
                        content.jsonObject["role"]?.jsonPrimitive?.content
                    )

                    // response parts
                    val parts = content.jsonObject["parts"]
                    val textMessage = parts?.singleTextMessageInParts()

                    if (textMessage != null) {
                        span.setAttribute("gen_ai.completion.$index.content", textMessage.orRedactedOutput())
                    } else {
                        span.setAttribute("gen_ai.completion.$index.content", parts.toString().orRedactedOutput())
                    }

                    // collect requests for a tool call
                    if (parts is JsonArray) {
                        var toolCallIndex = 0
                        for (part in parts.jsonArray) {
                            part.jsonObject["functionCall"]?.jsonObject?.let { part ->
                                val name = part["name"]?.jsonPrimitive?.content
                                val args = part["args"].toString()

                                span.setAttribute(
                                    "gen_ai.completion.$index.tool.$toolCallIndex.name",
                                    name?.orRedactedOutput()
                                )
                                span.setAttribute(
                                    "gen_ai.completion.$index.tool.$toolCallIndex.arguments",
                                    args.orRedactedOutput()
                                )
                                ++toolCallIndex
                            }
                        }
                    }
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
        val contentBuilders = mutableMapOf<Int, StringBuilder>()
        val roles = mutableMapOf<Int, String>()
        val finishReasons = mutableMapOf<Int, String>()
        val toolCallCounters = mutableMapOf<Int, Int>()

        for (line in events.lineSequence()) {
            if (!line.startsWith("data:")) continue
            val data = line.removePrefix("data:").trim()
            val chunk = runCatching { Json.parseToJsonElement(data).jsonObject }.getOrNull() ?: continue

            chunk["usageMetadata"]?.jsonObject?.let { setUsageAttributes(span, it) }

            val candidates = chunk["candidates"]?.jsonArray ?: continue
            for ((index, candidate) in candidates.withIndex()) {
                candidate.jsonObject["content"]?.jsonObject?.let { content ->
                    content["role"]?.jsonPrimitive?.content?.let { roles.putIfAbsent(index, it) }

                    content["parts"]?.jsonArray?.forEach { part ->
                        part.jsonObject["text"]?.jsonPrimitive?.content?.let {
                            contentBuilders.getOrPut(index) { StringBuilder() }.append(it)
                        }
                        part.jsonObject["functionCall"]?.jsonObject?.let { fc ->
                            val tcIndex = toolCallCounters.getOrPut(index) { 0 }
                            toolCallCounters[index] = tcIndex + 1

                            fc["name"]?.jsonPrimitive?.content?.let {
                                span.setAttribute("gen_ai.completion.$index.tool.$tcIndex.name", it.orRedactedOutput())
                            }
                            fc["args"]?.let {
                                span.setAttribute("gen_ai.completion.$index.tool.$tcIndex.arguments", it.toString().orRedactedOutput())
                            }
                        }
                    }
                }

                candidate.jsonObject["finishReason"]?.jsonPrimitive?.content?.let {
                    finishReasons[index] = it
                }
            }
        }

        for ((index, content) in contentBuilders) {
            span.setAttribute("gen_ai.completion.$index.content", content.toString().orRedactedOutput())
        }
        for ((index, role) in roles) {
            span.setAttribute("gen_ai.completion.$index.role", role)
        }
        for ((index, reason) in finishReasons) {
            span.setAttribute("gen_ai.completion.$index.finish_reason", reason)
        }
    }.getOrElse { exception ->
        span.setStatus(StatusCode.ERROR)
        span.recordException(exception)
    }

    /**
     * Sets usage attributes (promptTokenCount/candidatesTokenCount/totalTokenCount).
     *
     * The following two properties (`promptTokensDetails`, `candidatesTokensDetails`)
     * and their inner contents are mapped into snake-cased OTEL attributes.
     *
     * 1. For `promptTokensDetails`:
     *   - `"gen_ai.usage.prompt_tokens_details.0.modality"`
     *   - `"gen_ai.usage.prompt_tokens_details.0.token_count"`
     * 2. For `candidatesTokensDetails`:
     *   - `"gen_ai.usage.candidates_tokens_details.0.modality"`
     *   - `"gen_ai.usage.candidates_tokens_details.0.token_count"`
     *
     * See: https://ai.google.dev/api/generate-content#UsageMetadata
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
