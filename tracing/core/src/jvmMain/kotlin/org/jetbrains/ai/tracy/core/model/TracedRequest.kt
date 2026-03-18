/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.core.model

import org.jetbrains.ai.tracy.core.adapters.media.MediaContent
import org.jetbrains.ai.tracy.core.policy.ContentKind

/**
 * Unified representation of an LLM request, normalized from provider-specific formats.
 *
 * This is the internal model used in Step 2 of the tracing pipeline:
 * `JSON → Provider Data Class → TracedRequest → Span Attributes`
 *
 * Provider-specific normalizers convert their typed request models into this unified structure,
 * which is then serialized to OTel span attributes by [SpanSerializer].
 */
data class TracedRequest(
    // Generation config
    val model: String? = null,
    val temperature: Double? = null,
    val topP: Double? = null,
    val topK: Double? = null,
    val maxTokens: Long? = null,
    val candidateCount: Long? = null,
    val operationName: String? = null,

    // Messages
    val messages: List<TracedMessage> = emptyList(),
    val systemPrompt: TracedSystemPrompt? = null,

    // Tools
    val tools: List<TracedTool> = emptyList(),

    // Media
    val mediaContent: MediaContent? = null,

    /**
     * Provider-specific attributes that don't fit the common model.
     * Key = full attribute name (e.g., "gen_ai.metadata.user_id").
     */
    val extraAttributes: Map<String, Any?> = emptyMap(),
)

/**
 * A message in the conversation (request-side).
 */
data class TracedMessage(
    val role: String? = null,
    val content: String? = null,
    val contentKind: ContentKind = ContentKind.INPUT,
    val toolCallId: String? = null,
    /**
     * Additional per-message attributes serialized as `gen_ai.prompt.$index.$key`.
     */
    val extraAttributes: Map<String, String> = emptyMap(),
)

/**
 * System prompt. Anthropic supports both string and structured block formats.
 */
sealed class TracedSystemPrompt {
    data class Text(val text: String) : TracedSystemPrompt()
    data class Blocks(val blocks: List<SystemBlock>) : TracedSystemPrompt()
}

data class SystemBlock(val type: String?, val content: String?)

/**
 * A tool definition from the request.
 */
data class TracedTool(
    val type: String? = null,
    val name: String? = null,
    val description: String? = null,
    val parameters: String? = null,
    val strict: String? = null,
    /**
     * Gemini: nested function declarations within a single tool object.
     * OpenAI/Anthropic leave this null (each tool has a single function).
     */
    val functions: List<TracedToolFunction>? = null,
)

/**
 * A function declaration nested within a Gemini tool.
 */
data class TracedToolFunction(
    val type: String? = null,
    val name: String? = null,
    val description: String? = null,
    val parameters: String? = null,
)
