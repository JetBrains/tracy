/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.openai.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Provider-specific data classes for OpenAI Chat Completions API.
 *
 * These match the official OpenAI API schema and are used in Step 1 of the tracing pipeline
 * to deserialize raw JSON into typed objects. Only fields that Tracy actually reads are included.
 *
 * See: [Chat Completions API](https://platform.openai.com/docs/api-reference/chat/create)
 */

// --- Request ---

@Serializable
internal data class ChatCompletionRequest(
    val model: String? = null,
    val temperature: Double? = null,
    val messages: List<ChatMessage> = emptyList(),
    val tools: List<ChatTool>? = null,
)

@Serializable
internal data class ChatMessage(
    val role: String? = null,
    /** Polymorphic: can be a JSON string or an array of content parts. */
    val content: JsonElement? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
)

@Serializable
internal data class ChatTool(
    val type: String? = null,
    val function: ChatToolFunction? = null,
)

@Serializable
internal data class ChatToolFunction(
    val name: String? = null,
    val description: String? = null,
    val parameters: JsonObject? = null,
    val strict: Boolean? = null,
)

// --- Response ---

@Serializable
internal data class ChatCompletionResponse(
    val id: String? = null,
    @SerialName("object") val objectType: String? = null,
    val model: String? = null,
    val choices: List<ChatChoice> = emptyList(),
    val usage: ChatUsage? = null,
)

@Serializable
internal data class ChatChoice(
    val index: Int? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
    val message: ChatResponseMessage? = null,
)

@Serializable
internal data class ChatResponseMessage(
    val role: String? = null,
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ChatResponseToolCall>? = null,
    val annotations: JsonElement? = null,
)

@Serializable
internal data class ChatResponseToolCall(
    val id: String? = null,
    val type: String? = null,
    val function: ChatResponseToolCallFunction? = null,
)

@Serializable
internal data class ChatResponseToolCallFunction(
    val name: String? = null,
    val arguments: String? = null,
)

@Serializable
internal data class ChatUsage(
    @SerialName("prompt_tokens") val promptTokens: Int? = null,
    @SerialName("completion_tokens") val completionTokens: Int? = null,
)

// --- Streaming ---

@Serializable
internal data class ChatCompletionStreamChunk(
    val choices: List<ChatStreamChoice> = emptyList(),
)

@Serializable
internal data class ChatStreamChoice(
    val delta: ChatStreamDelta? = null,
)

@Serializable
internal data class ChatStreamDelta(
    val role: String? = null,
    val content: String? = null,
)
