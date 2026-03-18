/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.anthropic.adapters

import org.jetbrains.ai.tracy.anthropic.model.*
import org.jetbrains.ai.tracy.core.adapters.LLMTracingAdapter
import org.jetbrains.ai.tracy.core.adapters.LLMTracingAdapter.Companion.PayloadType
import org.jetbrains.ai.tracy.core.adapters.LLMTracingAdapter.Companion.populateUnmappedAttributes
import org.jetbrains.ai.tracy.core.adapters.media.*
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpRequest
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpResponse
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpUrl
import org.jetbrains.ai.tracy.core.http.protocol.asJson
import org.jetbrains.ai.tracy.core.model.*
import org.jetbrains.ai.tracy.core.policy.ContentKind
import org.jetbrains.ai.tracy.core.policy.contentTracingAllowed
import org.jetbrains.ai.tracy.core.policy.orRedactedOutput
import io.opentelemetry.api.trace.Span
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.*
import kotlinx.serialization.json.*
import mu.KotlinLogging

/**
 * Tracing adapter for Anthropic Claude API.
 *
 * Uses the 3-step tracing pipeline:
 * 1. **Parse**: JSON → [AnthropicMessagesRequest]/[AnthropicMessagesResponse]
 * 2. **Normalize**: Provider data classes → [TracedRequest]/[TracedResponse]
 * 3. **Serialize**: Unified model → span attributes via [SpanSerializer]
 *
 * See: [Anthropic Messages API](https://docs.claude.com/en/api/messages)
 */
class AnthropicLLMTracingAdapter : LLMTracingAdapter(genAISystem = GenAiSystemIncubatingValues.ANTHROPIC) {

    private val json = Json { ignoreUnknownKeys = true }

    override fun getRequestBodyAttributes(span: Span, request: TracyHttpRequest) {
        val body = request.body.asJson()?.jsonObject ?: return

        // Step 1: Parse
        val parsed = json.decodeFromJsonElement<AnthropicMessagesRequest>(body)

        // Step 2: Normalize
        val traced = normalizeRequest(parsed, body)

        // Step 3: Serialize
        SpanSerializer.writeRequest(span, traced, extractor)

        // Unmapped attributes (uses raw JsonObject — nothing is lost)
        span.populateUnmappedAttributes(body, mappedAttributes, PayloadType.REQUEST)
    }

    override fun getResponseBodyAttributes(span: Span, response: TracyHttpResponse) {
        val body = response.body.asJson()?.jsonObject ?: return

        // Step 1: Parse
        val parsed = json.decodeFromJsonElement<AnthropicMessagesResponse>(body)

        // Step 2: Normalize
        val traced = normalizeResponse(parsed)

        // Step 3: Serialize
        SpanSerializer.writeResponse(span, traced, extractor)

        // Unmapped attributes
        span.populateUnmappedAttributes(body, mappedAttributes, PayloadType.RESPONSE)
    }

    override fun getSpanName(request: TracyHttpRequest) = "Anthropic-generation"

    // streaming is not supported
    override fun isStreamingRequest(request: TracyHttpRequest) = false
    override fun handleStreaming(span: Span, url: TracyHttpUrl, events: String) = Unit

    // --- Step 2: Normalization ---

    private fun normalizeRequest(req: AnthropicMessagesRequest, rawBody: JsonObject): TracedRequest {
        val systemPrompt = parseSystemPrompt(req.system)

        val messages = req.messages?.map { msg ->
            TracedMessage(
                role = msg.role,
                content = msg.content?.toString(),
                // treat all request messages (including assistant history) as input per policy
                contentKind = ContentKind.INPUT,
            )
        } ?: emptyList()

        val tools = req.tools?.map { tool ->
            TracedTool(
                name = tool.name,
                description = tool.description,
                type = tool.type,
                parameters = tool.inputSchema?.toString(),
            )
        } ?: emptyList()

        // Media content extraction from raw body (needs JsonArray traversal)
        val mediaContent = if (contentTracingAllowed(ContentKind.INPUT)) {
            parseMediaContent(rawBody)
        } else null

        val extras = buildMap<String, Any?> {
            req.metadata?.userId?.let { put("gen_ai.metadata.user_id", it) }
            req.serviceTier?.let { put("gen_ai.usage.service_tier", it) }
        }

        return TracedRequest(
            model = req.model,
            temperature = req.temperature,
            topP = req.topP,
            topK = req.topK,
            maxTokens = req.maxTokens?.toLong(),
            systemPrompt = systemPrompt,
            messages = messages,
            tools = tools,
            mediaContent = mediaContent,
            extraAttributes = extras,
        )
    }

    private fun normalizeResponse(resp: AnthropicMessagesResponse): TracedResponse {
        val completions = resp.content?.map { block ->
            when (block.type) {
                "text" -> TracedCompletion(
                    type = block.type,
                    content = block.text,
                )

                "tool_use" -> {
                    // Anthropic uses flat tool attributes without a sub-index:
                    //   gen_ai.completion.$index.tool.call.id
                    //   gen_ai.completion.$index.tool.call.type
                    //   gen_ai.completion.$index.tool.name
                    //   gen_ai.completion.$index.tool.arguments
                    val toolExtras = buildMap {
                        block.id?.let { put("tool.call.id", it) }
                        block.type?.let { put("tool.call.type", it) }
                        block.name?.let { put("tool.name", it.orRedactedOutput()) }
                        block.input?.let { put("tool.arguments", it.toString().orRedactedOutput()) }
                    }
                    TracedCompletion(
                        type = block.type,
                        extraAttributes = toolExtras,
                    )
                }

                else -> TracedCompletion(
                    type = block.type,
                    // pass the whole block as content for unknown types
                    content = block.toString(),
                )
            }
        } ?: emptyList()

        val usage = resp.usage?.let { u ->
            val extras = buildMap<String, Any?> {
                u.cacheCreationInputTokens?.let { put("gen_ai.usage.cache_creation.input_tokens", it.toLong()) }
                u.cacheReadInputTokens?.let { put("gen_ai.usage.cache_read.input_tokens", it.toLong()) }
                u.serviceTier?.let { put("gen_ai.usage.service_tier", it) }
            }
            TracedUsage(
                inputTokens = u.inputTokens,
                outputTokens = u.outputTokens,
                extraAttributes = extras,
            )
        }

        val extras = buildMap<String, Any?> {
            resp.type?.let { put(GEN_AI_OUTPUT_TYPE.key, it) }
            resp.role?.let { put("gen_ai.response.role", it) }
        }

        return TracedResponse(
            id = resp.id,
            model = resp.model,
            completions = completions,
            finishReasons = listOfNotNull(resp.stopReason),
            usage = usage,
            extraAttributes = extras,
        )
    }

    // --- Helpers ---

    /**
     * Parses the polymorphic system prompt (string or array of blocks).
     */
    private fun parseSystemPrompt(system: JsonElement?): TracedSystemPrompt? = when (system) {
        is JsonPrimitive -> TracedSystemPrompt.Text(system.content)
        is JsonArray -> {
            val blocks = system.mapNotNull { block ->
                val obj = block.jsonObject
                SystemBlock(
                    type = obj["type"]?.jsonPrimitive?.content,
                    content = obj["text"]?.jsonPrimitive?.content,
                )
            }
            TracedSystemPrompt.Blocks(blocks)
        }
        else -> null
    }

    /**
     * Parses media content from the `messages` field when content type is
     * either `ImageBlockParam` or `DocumentBlockParam`.
     *
     * See [Messages API Docs](https://platform.claude.com/docs/en/api/messages/create)
     */
    private fun parseMediaContent(body: JsonObject): MediaContent? {
        if (body["messages"] !is JsonArray) return null
        val messages = body["messages"]?.jsonArray ?: return null

        val parts: List<MediaContentPart> = buildList {
            val supportedMessageTypes = listOf("image", "document")

            for (message in messages) {
                if (message !is JsonObject || message["content"] !is JsonArray) continue
                val content = message["content"]?.jsonArray ?: continue

                for (part in content) {
                    val messageType = part.jsonObject["type"]?.jsonPrimitive?.content ?: continue
                    if (messageType !in supportedMessageTypes) continue

                    val source = part.jsonObject["source"]?.jsonObject ?: continue
                    val contentParts = parseSource(messageType, source).map { MediaContentPart(it) }
                    addAll(contentParts)
                }
            }
        }

        return MediaContent(parts)
    }

    private fun parseSource(messageType: String, source: JsonObject): List<Resource> {
        val sourceType = source["type"]?.jsonPrimitive?.content ?: return emptyList()
        return when (sourceType) {
            "url" -> listOfNotNull(parseUrl(messageType, source))
            "base64" -> listOfNotNull(parseBase64(messageType, source))
            "content" -> parseContent(messageType, source)
            else -> emptyList()
        }
    }

    private fun parseUrl(messageType: String, source: JsonObject): Resource.Url? {
        val url = source["url"]?.jsonPrimitive?.content
        if (url == null) {
            logger.warn { "Message with type '$messageType' has no URL source" }
            return null
        }
        return Resource.Url(url)
    }

    private fun parseBase64(messageType: String, source: JsonObject): Resource.Base64? {
        val data = source["data"]?.jsonPrimitive?.content
        val mediaType = source["media_type"]?.jsonPrimitive?.content
        if (data == null || mediaType == null) {
            logger.warn { "Message with type '$messageType' misses either 'data' or 'media_type' attribute" }
            return null
        }
        return Resource.Base64(data, mediaType)
    }

    private fun parseContent(messageType: String, source: JsonObject): List<Resource> {
        val content = source["content"]
        if (content == null || content !is JsonArray) {
            logger.warn { "Message with type '$messageType' has no content source" }
            return emptyList()
        }

        return buildList {
            for (param in content.jsonArray) {
                val type = param.jsonObject["type"]?.jsonPrimitive?.content ?: continue
                if (type == "image") {
                    val imageSource = param.jsonObject["source"]?.jsonObject ?: continue
                    addAll(parseSource(messageType, imageSource))
                }
            }
        }
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
