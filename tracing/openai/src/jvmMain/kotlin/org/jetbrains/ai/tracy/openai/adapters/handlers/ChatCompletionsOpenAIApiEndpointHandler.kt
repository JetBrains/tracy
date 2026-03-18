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
import org.jetbrains.ai.tracy.core.model.*
import org.jetbrains.ai.tracy.core.policy.ContentKind
import org.jetbrains.ai.tracy.core.policy.contentTracingAllowed
import org.jetbrains.ai.tracy.core.policy.orRedacted
import org.jetbrains.ai.tracy.openai.model.*
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import kotlinx.serialization.json.*


/**
 * Handler for OpenAI Chat Completions API.
 *
 * Uses the 3-step tracing pipeline:
 * 1. **Parse**: JSON → [ChatCompletionRequest]/[ChatCompletionResponse]
 * 2. **Normalize**: Provider data classes → [TracedRequest]/[TracedResponse]
 * 3. **Serialize**: Unified model → span attributes via [SpanSerializer]
 */
internal class ChatCompletionsOpenAIApiEndpointHandler(
    private val extractor: MediaContentExtractor
) : EndpointApiHandler {

    private val json = Json { ignoreUnknownKeys = true }

    override fun handleRequestAttributes(span: Span, request: TracyHttpRequest) {
        val body = request.body.asJson()?.jsonObject ?: return

        // Step 1: Parse
        val parsed = json.decodeFromJsonElement<ChatCompletionRequest>(body)

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
        val parsed = json.decodeFromJsonElement<ChatCompletionResponse>(body)

        // Step 2: Normalize
        val traced = normalizeResponse(parsed)

        // Step 3: Serialize
        SpanSerializer.writeResponse(span, traced, extractor)

        // Unmapped attributes
        span.populateUnmappedAttributes(body, mappedAttributes, PayloadType.RESPONSE)
    }

    override fun handleStreaming(span: Span, events: String): Unit = runCatching {
        var role: String? = null
        val out = buildString {
            for (line in events.lineSequence()) {
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()

                val chunk = runCatching {
                    json.decodeFromString<ChatCompletionStreamChunk>(data)
                }.getOrNull() ?: continue

                val delta = chunk.choices.firstOrNull()?.delta ?: continue
                if (role == null) role = delta.role
                delta.content?.let { append(it) }
            }
        }

        if (out.isNotEmpty()) {
            val kind = kindByRole(role)
            span.setAttribute("gen_ai.completion.0.content", out.orRedacted(kind))
        }
        role?.let { span.setAttribute("gen_ai.completion.0.role", it) }

        return@runCatching
    }.getOrElse { exception ->
        span.setStatus(StatusCode.ERROR)
        span.recordException(exception)
    }

    // --- Step 2: Normalization ---

    private fun normalizeRequest(req: ChatCompletionRequest): TracedRequest {
        val messages = req.messages.map { msg ->
            val kind = kindByRole(msg.role)
            TracedMessage(
                role = msg.role,
                content = resolveContent(msg.content),
                contentKind = kind,
                toolCallId = msg.toolCallId,
            )
        }

        val tools = req.tools?.map { tool ->
            TracedTool(
                type = tool.type,
                name = tool.function?.name,
                description = tool.function?.description,
                parameters = tool.function?.parameters?.toString(),
                strict = tool.function?.strict?.toString(),
            )
        } ?: emptyList()

        // Collect media content from all messages with array content
        val mediaContent = extractMediaFromMessages(req.messages)

        return TracedRequest(
            model = req.model,
            temperature = req.temperature,
            messages = messages,
            tools = tools,
            mediaContent = mediaContent,
        )
    }

    private fun normalizeResponse(resp: ChatCompletionResponse): TracedResponse {
        return TracedResponse(
            id = resp.id,
            model = resp.model,
            operationName = resp.objectType,
            completions = resp.choices.map { choice ->
                TracedCompletion(
                    role = choice.message?.role,
                    content = choice.message?.content,
                    finishReason = choice.finishReason,
                    annotations = choice.message?.annotations?.toString(),
                    toolCalls = choice.message?.toolCalls?.map { tc ->
                        TracedToolCall(
                            id = tc.id,
                            type = tc.type,
                            name = tc.function?.name,
                            arguments = tc.function?.arguments,
                        )
                    } ?: emptyList(),
                )
            },
            usage = resp.usage?.let {
                TracedUsage(inputTokens = it.promptTokens, outputTokens = it.completionTokens)
            },
        )
    }

    // --- Helpers ---

    /**
     * Resolves polymorphic content (string | array | null) to a string.
     */
    private fun resolveContent(content: JsonElement?): String? = when (content) {
        null, is JsonNull -> null
        is JsonPrimitive -> content.content
        is JsonArray -> content.toString()
        else -> content.toString()
    }

    /**
     * Given a role, define what content kind matches it, either input or output.
     */
    private fun kindByRole(role: String?): ContentKind = when (role) {
        "developer", "system", "user" -> ContentKind.INPUT
        else -> ContentKind.OUTPUT
    }

    /**
     * Extracts media content parts (images, audio, files) from message content arrays.
     *
     * See: [Chat Completions API](https://platform.openai.com/docs/api-reference/chat/create)
     */
    private fun extractMediaFromMessages(messages: List<ChatMessage>): MediaContent? {
        val allParts = mutableListOf<MediaContentPart>()

        for (msg in messages) {
            val content = msg.content
            if (content !is JsonArray) continue

            val kind = kindByRole(msg.role)
            if (!contentTracingAllowed(kind)) continue

            for (part in content) {
                val type = part.jsonObject["type"]?.jsonPrimitive?.content ?: continue
                val mediaPart = parseMediaPart(type, part.jsonObject) ?: continue
                allParts.add(mediaPart)
            }
        }

        return if (allParts.isNotEmpty()) MediaContent(allParts) else null
    }

    /**
     * Parses a single media content part from a content array element.
     */
    private fun parseMediaPart(type: String, obj: JsonObject): MediaContentPart? = when (type) {
        "image_url" -> {
            val url = obj["image_url"]?.jsonObject?.get("url")?.jsonPrimitive?.content ?: return null
            when {
                url.isValidUrl() -> MediaContentPart(resource = Resource.Url(url))
                url.startsWith("data:") -> MediaContentPart(resource = Resource.InlineDataUrl(url))
                else -> null
            }
        }

        "input_audio" -> {
            val audioObj = obj["input_audio"]?.jsonObject ?: return null
            val data = audioObj["data"]?.jsonPrimitive?.content ?: return null
            val format = audioObj["format"]?.jsonPrimitive?.content ?: return null
            MediaContentPart(resource = Resource.Base64(data, mediaType = "audio/$format"))
        }

        "file" -> {
            val fileData = obj["file"]?.jsonObject?.get("file_data")?.jsonPrimitive?.content ?: return null
            MediaContentPart(resource = Resource.InlineDataUrl(fileData))
        }

        else -> null
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
