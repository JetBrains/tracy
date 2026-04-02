/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.test.utils.fixtures

import  org.junit.jupiter.api.TestInfo
import kotlin.jvm.optionals.getOrNull

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


/**
 * Creates a fixture tag from test information.
 *
 * For regular tests, returns the sanitized test name.
 * For parameterized tests, returns: `[sanitized-test-name]_[index ]-[parameterName ]`
 *
 * Examples:
 * - Regular test: `"test OpenAI chat completions"` → `"openai-chat-completions"`
 * - Parameterized test with display name `"[1] ContentCapturePolicy(captureInputs=false, captureOutputs=false)"`
 *   and test method `"test openai collects policies"` → `"openai-collects-policies_1-ContentCapturePolicy"`
 *
 * @param testInfo JUnit TestInfo containing the test method and display name
 * @return A unique fixture tag for the test
 */
fun createFixtureTag(testInfo: TestInfo): String {
    val testMethod = testInfo.testMethod.getOrNull()
    val annotations = testMethod?.annotations?.toList() ?: emptyList()
    val isParameterizedTest = annotations
        .map { it.annotationClass.simpleName }
        .any { it == "ParameterizedTest" }

    return if (isParameterizedTest) {
        // For parameterized tests: extract index and parameter name from the display name
        val baseName = testMethod?.name?.sanitizeForFixtureName() ?: "unnamed-parameterized-test"
        val displayName = testInfo.displayName

        // Extract index and parameter name
        val (index, paramName) = extractParameterInfo(displayName)

        if (index != null && paramName != null) {
            "${baseName}_case-${index}-${paramName}"
        } else {
            // Fallback if parsing fails
            baseName
        }
    } else {
        testInfo.displayName.sanitizeForFixtureName()
    }
}

/**
 * Extracts parameter index and name from JUnit parameterized test display name.
 *
 * Expected format: `"[index ] ParameterType(param1=value1, param2=value2, ...)"`
 *
 * Examples:
 * - `"[1] ContentCapturePolicy(captureInputs=false, captureOutputs=false)"` → (1, "ContentCapturePolicy")
 * - `"[2] ChatModel.GPT_4O_MINI"` → (2, "ChatModel")
 * - `"[3] String"` → (3, "String")
 *
 * @param displayName JUnit parameterized test display name
 * @return Pair of (index, parameterName) or (null, null) if parsing fails
 */
internal fun extractParameterInfo(displayName: String): Pair<String?, String?> {
    // Regex pattern to match: "[index] ParameterType(optionalContent)" or "[index] ParameterType"
    val pattern = Regex("""^\[(\d+)]\s+([^(]+)(?:\(.*\))?$""")
    val matchResult = pattern.find(displayName)

    return if (matchResult != null) {
        val index = matchResult.groupValues[1]
        val paramName = matchResult.groupValues[2].trim()
        Pair(index, paramName)
    } else {
        Pair(null, null)
    }
}


/**
 * Creates a filename (**WITH extension**) for the given fixture parameters.
 *
 * @param extension a file extension; it's acceptable to start it with comma (i.e., `.json` and `json` are treated identically).
 */
internal fun generateFixtureFilename(
    method: String,
    path: String,
    fixtureIndex: Int,
    extension: String,
    prefix: String = "",
): String {
    // Convert path like "/v1/chat/completions" to "chat-completions"
    val sanitizedPath = path
        .removePrefix("/v1/")
        .removePrefix("/")
        .replace("/", "-")
        .replace(Regex("[^a-zA-Z0-9-]"), "-")
        .lowercase()

    val extensionWithoutDot = extension.removePrefix(".")

    return if (prefix.isNotEmpty()) {
        "$prefix-${method.lowercase()}-$sanitizedPath-${fixtureIndex}.${extensionWithoutDot}"
    } else {
        "${method.lowercase()}-$sanitizedPath-${fixtureIndex}.${extensionWithoutDot}"
    }
}

/**
 * Generates a filename for storing response body content externally.
 *
 * The extension is determined based on the content type:
 * - `text/event-stream` → `.sse`
 * - `video/\*` → `.mp4`, `.avi`, etc.
 * - `image/\*` → `.png`, `.jpg`, etc.
 * - `application/json` → `.json`
 * - Other → `.in`
 *
 * @param method HTTP method (GET, POST, etc.)
 * @param path API endpoint path
 * @param fixtureIndex Index of the response in the sequence
 * @param contentType MIME type of the content
 * @return Filename with appropriate extension
 */
internal fun generateBodyFilename(
    method: String,
    path: String,
    fixtureIndex: Int,
    contentType: String?
): String {
    val extension = when {
        contentType == null -> "in"
        contentType.contains("event-stream", ignoreCase = true) -> "sse"
        contentType.startsWith("video/", ignoreCase = true) -> contentType.substringAfter("/").substringBefore(";")
        contentType.startsWith("image/", ignoreCase = true) -> contentType.substringAfter("/").substringBefore(";")
        contentType.startsWith("audio/", ignoreCase = true) -> contentType.substringAfter("/").substringBefore(";")
        contentType.startsWith("application/json", ignoreCase = true) -> "json"
        else -> "in"
    }
    return generateFixtureFilename(method, path, fixtureIndex, extension, prefix = "body")
}
