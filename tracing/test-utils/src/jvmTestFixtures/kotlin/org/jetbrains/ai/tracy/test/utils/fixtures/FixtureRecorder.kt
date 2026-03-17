/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.test.utils.fixtures

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/**
 * Records HTTP responses as test fixtures.
 *
 * This class intercepts HTTP responses, sanitizes them, and writes them to
 * the filesystem in a structured format that can be used by mock servers.
 */
class FixtureRecorder(
    private val fixturesDir: Path,
    private val sanitizer: ResponseSanitizer
) {
    private val json = Json { prettyPrint = true }

    /**
     * Records an HTTP response as a fixture file.
     *
     * @param method The HTTP method (GET, POST, etc.)
     * @param path The API endpoint path (e.g., "/v1/chat/completions")
     * @param statusCode The HTTP status code
     * @param headers The response headers
     * @param body The response body
     * @param fixtureName Optional custom fixture name (defaults to auto-generated name based on path)
     */
    fun record(
        method: String,
        path: String,
        statusCode: Int,
        headers: Map<String, List<String>>,
        body: String,
        fixtureName: String? = null
    ) {
        val contentType = headers["content-type"]?.firstOrNull()
            ?: headers["Content-Type"]?.firstOrNull()

        val sanitizedBody = sanitizer.sanitizeBody(body, contentType)
        val sanitizedHeaders = sanitizer.sanitizeHeaders(headers)

        val fixture = HttpFixture(
            method = method,
            path = path,
            statusCode = statusCode,
            headers = sanitizedHeaders,
            body = sanitizedBody
        )

        val fileName = fixtureName ?: generateFixtureName(method, path)
        val fixtureFile = fixturesDir.resolve("$fileName.json")

        // Ensure parent directories exist
        fixtureFile.parent?.createDirectories()

        // Write fixture to file
        val fixtureJson = json.encodeToString(fixture)
        fixtureFile.writeText(fixtureJson)

        println("Recorded fixture: ${fixtureFile.toAbsolutePath()}")
    }

    private fun generateFixtureName(method: String, path: String): String {
        // Convert path like "/v1/chat/completions" to "chat-completions"
        val sanitizedPath = path
            .removePrefix("/v1/")
            .removePrefix("/")
            .replace("/", "-")
            .replace(Regex("[^a-zA-Z0-9-]"), "-")
            .lowercase()

        return "${method.lowercase()}-$sanitizedPath"
    }
}

/**
 * Represents a recorded HTTP fixture.
 */
@Serializable
data class HttpFixture(
    val method: String,
    val path: String,
    val statusCode: Int,
    val headers: Map<String, List<String>>,
    val body: String
)
