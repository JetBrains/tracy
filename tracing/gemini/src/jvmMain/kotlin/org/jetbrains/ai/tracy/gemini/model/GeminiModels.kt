/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.gemini.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Provider-specific data classes for Gemini GenerateContent API.
 *
 * These match the official Gemini API schema and are used in Step 1 of the tracing pipeline
 * to deserialize raw JSON into typed objects. Only fields that Tracy actually reads are included.
 *
 * See: [Gemini GenerateContent API](https://ai.google.dev/api/generate-content)
 */

// --- Request ---

@Serializable
internal data class GenerateContentRequest(
    val contents: List<GeminiContent>? = null,
    val tools: List<GeminiTool>? = null,
    val generationConfig: GeminiGenerationConfig? = null,
)

@Serializable
internal data class GeminiContent(
    val role: String? = null,
    /** Array of parts: text, inlineData, functionCall, functionResponse, etc. */
    val parts: List<JsonElement>? = null,
)

@Serializable
internal data class GeminiTool(
    val functionDeclarations: List<GeminiFunctionDeclaration>? = null,
)

@Serializable
internal data class GeminiFunctionDeclaration(
    val name: String? = null,
    val description: String? = null,
    val parameters: JsonObject? = null,
    val parametersJsonSchema: JsonObject? = null,
)

@Serializable
internal data class GeminiGenerationConfig(
    val temperature: Double? = null,
    val topP: Double? = null,
    val topK: Double? = null,
    val maxOutputTokens: Int? = null,
    val candidateCount: Int? = null,
)

// --- Response ---

@Serializable
internal data class GenerateContentResponse(
    val responseId: String? = null,
    val modelVersion: String? = null,
    val candidates: List<GeminiCandidate>? = null,
    val usageMetadata: GeminiUsageMetadata? = null,
)

@Serializable
internal data class GeminiCandidate(
    val content: GeminiContent? = null,
    val finishReason: String? = null,
)

@Serializable
internal data class GeminiUsageMetadata(
    val promptTokenCount: Int? = null,
    val candidatesTokenCount: Int? = null,
    val totalTokenCount: Int? = null,
    val promptTokensDetails: List<GeminiTokenDetail>? = null,
    val candidatesTokensDetails: List<GeminiTokenDetail>? = null,
)

@Serializable
internal data class GeminiTokenDetail(
    val modality: String? = null,
    val tokenCount: Int? = null,
)
