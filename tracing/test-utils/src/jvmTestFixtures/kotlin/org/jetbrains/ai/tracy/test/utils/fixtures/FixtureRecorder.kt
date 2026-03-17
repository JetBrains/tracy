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
    private val sanitizer: ResponseSanitizer,
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
     * @param fixtureTag tag to use as a folder where mocked responses are stored (usually, a test case name)
     * @param responseIndex the index of the response in the sequence of responses
     */
    fun record(
        method: String,
        path: String,
        statusCode: Int,
        headers: Map<String, List<String>>,
        body: String,
        fixtureTag: String,
        responseIndex: Int,
    ) {
        val contentType = headers["content-type"]?.firstOrNull()
            ?: headers["Content-Type"]?.firstOrNull()

        val sanitizedBody = sanitizer.sanitizeBody(body, contentType)
        val sanitizedHeaders = sanitizer.sanitizeHeaders(headers)

        val filename = generateFixtureFilename(method, path, responseIndex, extension = "json")
        // filepath will be: `$fixturesDir/$fixtureTag/$filename.json`
        val fixtureFile = fixturesDir.resolve(fixtureTag).resolve(filename)
        // ensure parent directories exist
        fixtureFile.parent?.createDirectories()

        val fixture = HttpFixture(
            method = method,
            path = path,
            statusCode = statusCode,
            headers = sanitizedHeaders,
            body = sanitizedBody,
            details = FixtureDetails(
                filename = filename,
                tag = fixtureTag,
                index = responseIndex,
            )
        )

        // Write fixture to a file
        val fixtureJson = json.encodeToString(fixture)
        fixtureFile.writeText(fixtureJson)

        println("Recorded fixture: ${fixtureFile.toAbsolutePath()}")
    }
}

/**
 * Represents a recorded HTTP response fixture.
 */
@Serializable
data class HttpFixture(
    val method: String,
    val path: String,
    val statusCode: Int,
    val headers: Map<String, List<String>>,
    val body: String,
    val details: FixtureDetails,
)

@Serializable
data class FixtureDetails(
    /**
     * Expected to contain an extension as well (e.g., `my-file.json`)
     */
    val filename: String,
    val tag: String,
    val index: Int,
)