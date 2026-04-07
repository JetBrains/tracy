/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.test.utils.fixtures

/**
 * Sanitizes HTTP responses by removing or replacing non-deterministic fields.
 *
 * This is used when recording test fixtures to ensure that fields like timestamps,
 * IDs, and AI-generated content are normalized to deterministic values.
 */
interface ResponseSanitizer {
    /**
     * Sanitizes the response body (typically JSON).
     *
     * @param body The raw response body as a string
     * @param mimeType The content type's MIME type of the response (e.g., "application/json")
     * @return The sanitized response body
     */
    fun sanitizeBody(body: String, mimeType: String?): String

    /**
     * Sanitizes HTTP headers by removing or normalizing non-deterministic values.
     *
     * @param headers The original headers
     * @return The sanitized headers
     */
    fun sanitizeHeaders(headers: Map<String, List<String>>): Map<String, List<String>>
}
