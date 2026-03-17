/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.test.utils.fixtures

import kotlinx.serialization.json.Json
import mu.KotlinLogging
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import java.io.File
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * Header name for specifying fixture tag in MOCK mode.
 * Clients should add this header to requests to match specific fixture variants.
 */
const val FIXTURE_TAG_HEADER = "X-Tracy-Fixture-Tag"

/**
 * Manages a mock HTTP server that serves responses from fixture files.
 *
 * This class loads HTTP fixtures from a directory and serves them via
 * [MockWebServer], matching requests to fixtures based on HTTP method and path.
 *
 * Supports fixture tags for unique test scenarios via the `X-Tracy-Fixture-Tag` header.
 */
class FixtureMockServer(private val fixturesDir: Path) {
    private val server = MockWebServer()

    /**
     * Starts the mock server and loads the test fixtures from the fixtures directory.
     */
    fun start() {
        val fixtures = loadFixtures(fixturesDir)
            ?: error("Failed to load fixtures from directory: ${fixturesDir.toAbsolutePath()}")

        server.dispatcher = FixtureDispatcher(fixtures)
        server.start()
    }

    /**
     * @return The base URL of the mock server appended with `/v1` suffix (e.g., "http://localhost:12345/v1")
     */
    fun url(): String = server.url("/v1").toString().removeSuffix("/")

    /**
     * Stops the mock server.
     */
    fun stop() {
        server.shutdown()
    }

    companion object {
        private fun loadFixtures(fixturesDir: Path): Map<String, HttpFixture>? {
            if (!fixturesDir.exists()) {
                logger.error { "Fixtures directory does not exist: ${fixturesDir.toAbsolutePath()}" }
                return null
            }

            val fixtureFiles = File(fixturesDir.toUri()).walkTopDown()
                .filter { it.isFile && it.extension == "json" }

            val fixtures = buildMap {
                for (file in fixtureFiles) {
                    try {
                        val fixtureJson = file.readText()
                        val fixture = json.decodeFromString<HttpFixture>(fixtureJson)

                        // Extract tag from filename if present
                        // Expected format: [method]-[path]-[tag].json
                        // e.g., "post-chat-completions-test-error-handling.json"
                        val tag = extractTagFromFilename(file.nameWithoutExtension, fixture.method, fixture.path)

                        val key = fixtureKeyOf(fixture.method, fixture.path, tag)

                        // assign the loaded fixture to the key
                        put(key, fixture)

                        logger.info { "Loaded fixture: ${file.name} -> $key" }
                    } catch (e: Exception) {
                        logger.trace(e) { "Failed to load fixture ${file.name}: ${e.message}" }
                    }
                }
            }

            logger.info { "Loaded ${fixtures.size} fixtures from ${fixturesDir.toAbsolutePath()}" }
            return fixtures
        }

        /**
         * Extracts the tag from a fixture filename.
         *
         * Given filename format: `[method]-[path]-tag.json`
         * Returns the tag portion if present, or null if the filename only contains `method-path`.
         *
         * Examples:
         * - "post-chat-completions-test-error-handling" → "test-error-handling"
         * - "post-chat-completions" → null
         */
        private fun extractTagFromFilename(filenameWithoutExt: String, method: String, path: String): String? {
            // Reconstruct what the base name (without tag) would look like
            val sanitizedPath = path
                .removePrefix("/v1/")
                .removePrefix("/")
                .replace("/", "-")
                .replace(Regex("[^a-zA-Z0-9-]"), "-")
                .lowercase()

            val expectedBaseName = "${method.lowercase()}-$sanitizedPath"

            // If filename is longer than the base name, the rest is the tag
            return if (filenameWithoutExt.startsWith(expectedBaseName) && filenameWithoutExt.length > expectedBaseName.length) {
                // Extract tag: everything after the base name, removing the leading hyphen
                filenameWithoutExt.substring(expectedBaseName.length).removePrefix("-")
            } else {
                null
            }
        }

        private val json = Json { ignoreUnknownKeys = true }
        private val logger = KotlinLogging.logger {}
    }
}

private class FixtureDispatcher(
    private val fixtures: Map<String, HttpFixture>
) : Dispatcher() {
    override fun dispatch(request: RecordedRequest): MockResponse {
        println("FixtureDispatcher.dispatch")
        println("=".repeat(25))
        println("Request path: ${request.path}")
        println("Request method: ${request.method}")
        println("Request headers:\n\t${request.headers.toMultimap().toList().joinToString("\n\t") { (k, v) -> "$k: $v" }}")
        println("=".repeat(25))

        val path = request.path ?: return notFoundResponse("Request path is null")
        val method = request.method ?: return notFoundResponse("Request method is null")

        // extract the fixture tag from the header if present
        val fixtureTag = request.getHeader(FIXTURE_TAG_HEADER)

        // build a list of possible fixture keys to try, in order of specificity:
        // 1. With tag (if provided)
        // 2. Without tag (fallback)
        // 3. Fuzzy match with tag (path without query params)
        // 4. Fuzzy match without tag
        val keys = buildList {
            val basePath = path
            val fuzzyPath = path.substringBefore('?')

            if (fixtureTag != null) {
                // try tagged fixtures first
                add(fixtureKeyOf(method, basePath, fixtureTag))
                if (fuzzyPath != basePath) {
                    add(fixtureKeyOf(method, fuzzyPath, fixtureTag))
                }
            }
        }

        println("[Dispatcher] keys: $keys")

        val fixture = keys.firstNotNullOfOrNull { fixtures[it] }

        if (fixture == null) {
            val errorMessage = """
                No fixture found for keys: ${keys.joinToString(", ") { "`$it`" }}
                Available fixtures: ${fixtures.keys.joinToString(", ")}.
                ${if (fixtureTag != null) "Ensure fixture response for tag '${fixtureTag}' exists" 
                 else "No fixture tag provided in request headers, ensure '$FIXTURE_TAG_HEADER' header is set to a fixture tag"}
            """.trimIndent()
            logger.error { errorMessage }
            return notFoundResponse(errorMessage)
        }

        println("[Dispatcher] found fixture for key: ${fixtures.filterValues { it == fixture }.keys.firstOrNull()}")

        return MockResponse()
            .setResponseCode(fixture.statusCode)
            .apply {
                // adding response headers from the fixture
                for ((name, values) in fixture.headers) {
                    for (value in values) {
                        addHeader(name, value)
                    }
                }
            }
            .setBody(fixture.body)
    }

    companion object {
        private val logger = KotlinLogging.logger {}

        private fun notFoundResponse(message: String?): MockResponse {
            return MockResponse()
                .setResponseCode(404)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"error\": \"${message ?: "Fixture Not Found"}\"}")
        }
    }
}

private fun fixtureKeyOf(method: String, path: String, tag: String? = null): String {
    val basePath = path.removePrefix("/v1")
    return if (tag != null) {
        "${method.uppercase()}:$basePath:$tag"
    } else {
        "${method.uppercase()}:$basePath"
    }
}

