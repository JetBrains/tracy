/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.anthropic.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Provider-specific data classes for Anthropic Messages API.
 *
 * These match the official Anthropic API schema and are used in Step 1 of the tracing pipeline
 * to deserialize raw JSON into typed objects. Only fields that Tracy actually reads are included.
 *
 * See: [Anthropic Messages API](https://platform.claude.com/docs/en/api/messages)
 */

// --- Request ---

@Serializable
internal data class AnthropicMessagesRequest(
    val model: String? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    val temperature: Double? = null,
    @SerialName("top_k") val topK: Double? = null,
    @SerialName("top_p") val topP: Double? = null,
    /** Polymorphic: can be a string or an array of text blocks. */
    val system: JsonElement? = null,
    val messages: List<AnthropicMessage>? = null,
    val tools: List<AnthropicTool>? = null,
    val metadata: AnthropicMetadata? = null,
    @SerialName("service_tier") val serviceTier: String? = null,
)

@Serializable
internal data class AnthropicMessage(
    val role: String? = null,
    /** Polymorphic: can be a string or an array of content blocks. */
    val content: JsonElement? = null,
)

@Serializable
internal data class AnthropicTool(
    val name: String? = null,
    val description: String? = null,
    val type: String? = null,
    @SerialName("input_schema") val inputSchema: JsonObject? = null,
)

@Serializable
internal data class AnthropicMetadata(
    @SerialName("user_id") val userId: String? = null,
)

// --- Response ---

@Serializable
internal data class AnthropicMessagesResponse(
    val id: String? = null,
    val type: String? = null,
    val role: String? = null,
    val model: String? = null,
    val content: List<AnthropicContentBlock>? = null,
    @SerialName("stop_reason") val stopReason: String? = null,
    val usage: AnthropicUsage? = null,
)

@Serializable
internal data class AnthropicContentBlock(
    val type: String? = null,
    // for type="text"
    val text: String? = null,
    // for type="tool_use"
    val id: String? = null,
    val name: String? = null,
    val input: JsonElement? = null,
)

@Serializable
internal data class AnthropicUsage(
    @SerialName("input_tokens") val inputTokens: Int? = null,
    @SerialName("output_tokens") val outputTokens: Int? = null,
    @SerialName("cache_creation_input_tokens") val cacheCreationInputTokens: Int? = null,
    @SerialName("cache_read_input_tokens") val cacheReadInputTokens: Int? = null,
    @SerialName("service_tier") val serviceTier: String? = null,
)
