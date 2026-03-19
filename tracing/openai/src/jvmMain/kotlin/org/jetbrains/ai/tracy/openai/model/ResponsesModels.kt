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
 * Provider-specific data classes for OpenAI Responses API.
 *
 * These match the official OpenAI API schema and are used in Step 1 of the tracing pipeline
 * to deserialize raw JSON into typed objects. Only fields that Tracy actually reads are included.
 *
 * See: [Responses API](https://platform.openai.com/docs/api-reference/responses)
 */

// --- Request ---

@Serializable
internal data class ResponsesRequest(
    val model: String? = null,
    val temperature: Double? = null,
    @SerialName("top_p") val topP: Double? = null,
    @SerialName("max_output_tokens") val maxOutputTokens: Long? = null,
    val instructions: String? = null,
    /** Polymorphic: can be a string or an array of input items. */
    val input: JsonElement? = null,
    val tools: List<ResponsesTool>? = null,
    @SerialName("previous_response_id") val previousResponseId: String? = null,
    val store: Boolean? = null,
    val truncation: String? = null,
    @SerialName("parallel_tool_calls") val parallelToolCalls: Boolean? = null,
    val stream: Boolean? = null,
    @SerialName("response_format") val responseFormat: JsonElement? = null,
    @SerialName("tool_choice") val toolChoice: JsonElement? = null,
    val reasoning: JsonElement? = null,
    val text: JsonElement? = null,
)

@Serializable
internal data class ResponsesTool(
    val type: String? = null,
    val name: String? = null,
    val description: String? = null,
    val parameters: JsonObject? = null,
    val strict: Boolean? = null,
)

// --- Response ---

@Serializable
internal data class ResponsesResponse(
    val id: String? = null,
    @SerialName("object") val objectType: String? = null,
    val model: String? = null,
    val output: List<JsonElement> = emptyList(),
    val usage: ResponsesUsage? = null,
)

@Serializable
internal data class ResponsesUsage(
    @SerialName("input_tokens") val inputTokens: Int? = null,
    @SerialName("output_tokens") val outputTokens: Int? = null,
)

// --- Streaming ---

@Serializable
internal data class ResponsesStreamEvent(
    val type: String? = null,
    val text: String? = null,
)
