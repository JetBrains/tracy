/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.core.model

import org.jetbrains.ai.tracy.core.adapters.media.MediaContent

/**
 * Unified representation of an LLM response, normalized from provider-specific formats.
 *
 * This is the internal model used in Step 2 of the tracing pipeline:
 * `JSON → Provider Data Class → TracedResponse → Span Attributes`
 *
 * Provider-specific normalizers convert their typed response models into this unified structure,
 * which is then serialized to OTel span attributes by [SpanSerializer].
 */
data class TracedResponse(
    val id: String? = null,
    val model: String? = null,
    val operationName: String? = null,

    // Completions (choices / content blocks / candidates)
    val completions: List<TracedCompletion> = emptyList(),

    // Usage
    val usage: TracedUsage? = null,

    // Top-level finish reasons (Anthropic: single stop_reason mapped to list)
    val finishReasons: List<String> = emptyList(),

    // Media
    val mediaContent: MediaContent? = null,

    /**
     * Provider-specific attributes that don't fit the common model.
     * Key = full attribute name (e.g., "gen_ai.response.role").
     */
    val extraAttributes: Map<String, Any?> = emptyMap(),
)

/**
 * A single completion (choice / content block / candidate) in the response.
 */
data class TracedCompletion(
    val role: String? = null,
    val content: String? = null,
    val finishReason: String? = null,
    val type: String? = null,
    val annotations: String? = null,
    val toolCalls: List<TracedToolCall> = emptyList(),
    /**
     * Additional per-completion attributes serialized as `gen_ai.completion.$index.$key`.
     */
    val extraAttributes: Map<String, String> = emptyMap(),
)

/**
 * A tool call in the response (function call by the model).
 */
data class TracedToolCall(
    val id: String? = null,
    val type: String? = null,
    val name: String? = null,
    val arguments: String? = null,
)

/**
 * Token usage statistics.
 */
data class TracedUsage(
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    /**
     * Provider-specific usage attributes (cache tokens, total tokens, modality breakdown).
     * Key = full attribute name (e.g., "gen_ai.usage.total_tokens").
     */
    val extraAttributes: Map<String, Any?> = emptyMap(),
)
