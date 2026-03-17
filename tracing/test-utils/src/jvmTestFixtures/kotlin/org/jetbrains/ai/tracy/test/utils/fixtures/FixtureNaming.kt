/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.test.utils.fixtures

/**
 * Sanitizes a test name to be used as part of a fixture file name.
 *
 * Removes or replaces characters that are invalid in file names and
 * converts the name to a readable, URL-safe format.
 *
 * Examples:
 * - `"test OpenAI chat completions auto tracing"` → `"test-openai-chat-completions-auto-tracing"`
 * - `"test capture policy hides sensitive data(ContentCapturePolicy@123)"` → `"test-capture-policy-hides-sensitive-data"`
 */
fun String.sanitizeForFixtureName(): String {
    return this
        // Remove common test prefixes for brevity
        .removePrefix("test ")
        .removePrefix("Test ")
        // Remove backticks from Kotlin test names
        .replace("`", "")
        // Remove parentheses and their contents (from parameterized tests)
        .replace(Regex("\\([^)]*\\)"), "")
        // Replace spaces and underscores with hyphens
        .replace(Regex("[\\s_]+"), "-")
        // Remove special characters except hyphens and alphanumerics
        .replace(Regex("[^a-zA-Z0-9-]"), "")
        // Collapse multiple hyphens into one
        .replace(Regex("-+"), "-")
        // Remove leading/trailing hyphens
        .trim('-')
        // Convert to lowercase for consistency
        .lowercase()
        // Limit length to avoid excessively long file names
        .take(FIXTURE_NAME_MAX_LENGTH)
}

private const val FIXTURE_NAME_MAX_LENGTH = 128
