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
 * Manages a mock HTTP server that serves responses from fixture files.
 *
 * This class loads HTTP fixtures from a directory and serves them via
 * [MockWebServer], matching requests to fixtures based on HTTP method and path.
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
                        val key = fixtureKeyOf(fixture.method, fixture.path)

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

        val path = request.path ?: return notFoundResponse()
        val method = request.method ?: "GET"

        val keys = listOf(
            // insert a normal key with the original path
            fixtureKeyOf(method, path),
            // try fuzzy match (path without query parameters)
            fixtureKeyOf(method, path = path.substringBefore('?')),
        )

        val fixture = keys.firstNotNullOfOrNull { fixtures[it] }
            ?: return notFoundResponse().also {
                logger.warn { "No fixture found for keys: ${keys.joinToString(", ") { "`$it`" }}" }
                logger.warn { "Available fixtures: ${fixtures.keys.joinToString(", ")}" }
            }

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

        private fun notFoundResponse(): MockResponse {
            return MockResponse()
                .setResponseCode(404)
                .setBody("{\"error\": \"No fixture found for this request\"}")
        }
    }
}

private fun fixtureKeyOf(method: String, path: String): String {
    return "${method.uppercase()}:${path.removePrefix("/v1")}"
}

