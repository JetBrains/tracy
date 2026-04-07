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
    private val json = Json { prettyPrint = true }

    override fun sanitizeBody(body: String, mimeType: String?): String {
        // TODO: temp no sanitize
        return body

        if (mimeType == null || !mimeType.contains("application/json")) {
            return body
        }

        return try {
            val jsonElement = Json.parseToJsonElement(body)
            val sanitized = sanitizeJsonElement(jsonElement)
            json.encodeToString(JsonElement.serializer(), sanitized)
        } catch (e: Exception) {
            // If JSON parsing fails, return original body
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

        // Sanitize timestamp fields
        if (mutableMap.containsKey("created")) {
            mutableMap["created"] = JsonPrimitive(1234567890)
        }
        if (mutableMap.containsKey("created_at")) {
            mutableMap["created_at"] = JsonPrimitive(1234567890)
        }

        // Sanitize AI-generated content in messages
        if (mutableMap.containsKey("content") && mutableMap.containsKey("role")) {
            val role = (mutableMap["role"] as? JsonPrimitive)?.contentOrNull
            if (role == "assistant") {
                mutableMap["content"] = JsonPrimitive("Sanitized AI response")
            }
        }

        // Sanitize tool call arguments
        if (mutableMap.containsKey("arguments") && mutableMap.containsKey("name")) {
            mutableMap["arguments"] = JsonPrimitive("{}")
        }

        // Recursively sanitize nested objects and arrays
        for ((key, value) in mutableMap.toMap()) {
            mutableMap[key] = sanitizeJsonElement(value)
        }

        return JsonObject(mutableMap)
    }

    override fun sanitizeHeaders(headers: Map<String, List<String>>): Map<String, List<String>> {
        val sanitized = headers.toMutableMap()

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

        return sanitized
    }
}
