/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.test.utils.fixtures

import kotlinx.serialization.json.*

/**
 * Sanitizer for OpenAI API responses.
 *
 * Removes or replaces non-deterministic fields, such as:
 * - IDs (request_id, id, etc.)
 * - Timestamps (created, created_at, etc.)
 * - AI-generated content (text responses, tool arguments)
 * - Rate limit headers
 */
class OpenAISanitizer : ResponseSanitizer {
    private val json = Json { prettyPrint = false }

    // mentioning non-deterministic/unwanted headers that should be omitted
    private val headersToDrop = listOf(
        // headers returned by https://api.openai.com/v1 endpoint:
        "alt-svc", "cf-ray", "server", "set-cookie", "openai-processing-ms",
        "cf-cache-status", "x-content-type-options",
        // other headers (unprefixed LiteLLM headers):
        "connection", "vary", "content-encoding", "transfer-encoding",
    )

    override fun sanitizeBody(body: String, mimeType: String?): String {
        if (mimeType == null || !mimeType.contains("application/json")) {
            return body
        }

        return try {
            val jsonElement = Json.parseToJsonElement(body)
            val sanitized = sanitizeJsonElement(jsonElement)
            json.encodeToString(sanitized)
        } catch (_: Exception) {
            // If JSON parsing fails, return the original body
            body
        }
    }

    private fun sanitizeJsonElement(element: JsonElement): JsonElement {
        return when (element) {
            is JsonObject -> sanitizeJsonObject(element)
            is JsonArray -> JsonArray(element.map { sanitizeJsonElement(it) })
            else -> element
        }
    }

    private fun sanitizeJsonObject(obj: JsonObject): JsonObject {
        val mutableMap = obj.toMutableMap()

        // Sanitize ID fields
        if (mutableMap.containsKey("id")) {
            mutableMap["id"] = JsonPrimitive("sanitized-id")
        }
        if (mutableMap.containsKey("request_id")) {
            mutableMap["request_id"] = JsonPrimitive("sanitized-request-id")
        }
        if (mutableMap.containsKey("organization_id")) {
            mutableMap["organization_id"] = JsonPrimitive("sanitized-org-id")
        }
        if (mutableMap.containsKey("project_id")) {
            mutableMap["project_id"] = JsonPrimitive("sanitized-project-id")
        }
        if (mutableMap.containsKey("call_id")) {
            mutableMap["call_id"] = JsonPrimitive("sanitized-call-id")
        }
        if (mutableMap.containsKey("system_fingerprint")) {
            mutableMap["system_fingerprint"] = JsonPrimitive("sanitized-system-fingerprint")
        }

        // Sanitize timestamp fields
        val timestampKeys = listOf("created", "created_at", "completed_at", "expires_at")
        for (timestamp in timestampKeys) {
            if (mutableMap.containsKey(timestamp)) {
                mutableMap[timestamp] = JsonPrimitive(1234567890)
            }
        }

        // Sanitize AI-generated content in messages
        if (mutableMap.containsKey("content") && mutableMap.containsKey("role")) {
            val role = (mutableMap["role"] as? JsonPrimitive)?.contentOrNull
            if (role == "assistant") {
                mutableMap["content"] = JsonPrimitive("sanitized-assistant-response")
            }
        }

        // Sanitize tool call arguments
        if (mutableMap.containsKey("arguments") && mutableMap.containsKey("name")) {
            mutableMap["arguments"] = JsonPrimitive("{}")
        }

        // Sanitize usage tokens
        val usageKeys = listOf("input_tokens", "output_tokens", "total_tokens")
        if (usageKeys.all { mutableMap.containsKey(it) }) {
            val inputTokens = mutableMap["input_tokens"]!!.jsonPrimitive.int
            // placeholder value for output tokens of 42
            mutableMap["output_tokens"] = JsonPrimitive(42)
            mutableMap["total_tokens"] = JsonPrimitive(inputTokens + 42)
        }

        // Recursively sanitize nested objects and arrays
        for ((key, value) in mutableMap.toMap()) {
            mutableMap[key] = sanitizeJsonElement(value)
        }

        return JsonObject(mutableMap)
    }

    override fun sanitizeHeaders(headers: Map<String, List<String>>): Map<String, List<String>> {
        val sanitized = headers.toMutableMap()

        // remove x-litellm headers and remove prefix of `llm_provider`
        for (header in sanitized.keys.toList()) {
            // remove x-litellm headers
            if (header.startsWith("x-litellm-")) {
                sanitized.remove(header)
            }
            // remove prefix of `llm_provider`
            if (header.startsWith("llm_provider-")) {
                val updatedHeader = header.removePrefix("llm_provider-")
                val value = sanitized[header] ?: continue

                sanitized.remove(header)
                sanitized[updatedHeader] = value
            }
        }

        // Remove rate limit headers (non-deterministic)
        val rateLimitKeys = headers.keys.filter {
            it.lowercase().startsWith("x-ratelimit") ||
            it.lowercase().startsWith("ratelimit")
        }
        rateLimitKeys.forEach { sanitized.remove(it) }

        // Remove request ID headers
        sanitized.remove("x-request-id")
        sanitized.remove("X-Request-ID")
        sanitized.remove("request-id")

        // Normalize date headers
        if (sanitized.containsKey("date") || sanitized.containsKey("Date")) {
            sanitized["Date"] = listOf("Mon, 01 Jan 2024 00:00:00 GMT")
            sanitized.remove("date")
        }

        if (sanitized.containsKey("openai-organization")) {
            sanitized.replace("openai-organization", listOf("sanitized-org-id"))
        }
        if (sanitized.containsKey("openai-project")) {
            sanitized.replace("openai-project", listOf("sanitized-project-id"))
        }
        if (sanitized.containsKey("openai-processing-ms")) {
            sanitized.replace("openai-processing-ms", listOf("1000"))
        }

        // remove headers to drop:
        // remove at the end to prioritize caller's decision
        for (header in headersToDrop) {
            sanitized.remove(header)
        }

        return sanitized.toSortedMap()
    }
}
