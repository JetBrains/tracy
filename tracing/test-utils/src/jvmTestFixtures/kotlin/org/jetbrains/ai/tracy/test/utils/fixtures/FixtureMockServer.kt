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
import kotlin.io.path.readBytes

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

        server.dispatcher = FixtureDispatcher(fixtures, fixturesDir)
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

                        val key = fixture.fixtureKey()

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
    private val fixtures: Map<String, HttpFixture>,
    private val fixturesDir: Path,
) : Dispatcher() {
    // fixture tag -> number of already dispatched requests
    private val dispatchedRequestsCountPerFixtureTag = mutableMapOf<String, Int>()

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
        val fixtureTag = request.getHeader(FIXTURE_TAG_HEADER) ?: return notFoundResponse(
            "No fixture tag provided in request headers, ensure '$FIXTURE_TAG_HEADER' header is set to a fixture tag"
        )

        val dispatchedRequestsCount = dispatchedRequestsCountPerFixtureTag.getOrPut(fixtureTag) { 0 }

        // build a list of possible fixture keys to try, in order of specificity:
        // 1. With tag (if provided)
        // 2. Without tag (fallback)
        // 3. Fuzzy match with tag (path without query params)
        // 4. Fuzzy match without tag
        val keys = buildList {
            val exactKey = fixtureKey(method, path, fixtureTag, index = dispatchedRequestsCount)

            // dropping query params
            val fuzzyKey = fixtureKey(
                method = method,
                path = path.substringBefore('?'),
                tag = fixtureTag,
                index = dispatchedRequestsCount,
            )

            add(exactKey)
            add(fuzzyKey)
        }

        println("[Dispatcher] keys: $keys")

        val fixture = keys.firstNotNullOfOrNull { fixtures[it] }

        println("[Dispatcher] registered fixture keys: ${fixtures.keys.toList()}")
        println("[Dispatcher] found fixture: $fixture")

        if (fixture == null) {
            val errorMessage = """
                No fixture found for keys: ${keys.joinToString(", ") { "`$it`" }}
                Available fixture keys: ${fixtures.keys.joinToString(", ")}.
                "Ensure fixture response associated with a fixture tag '${fixtureTag}' exists"
            """.trimIndent()
            logger.error { errorMessage }
            return notFoundResponse(errorMessage)
        }

        // this request was successfully dispatched to an existing fixture response
        dispatchedRequestsCountPerFixtureTag[fixtureTag] = dispatchedRequestsCount + 1

        // Load the body content based on storage type
        val bodyContent = when (val body = fixture.body) {
            is FixtureBody.Inline -> body.content
            is FixtureBody.ExternalFile -> {
                val bodyFile = fixturesDir.resolve(fixture.details.tag).resolve(body.relativePath)
                try {
                    // TODO: is it possible to read the body as a stream instead?
                    bodyFile.readBytes().decodeToString()
                } catch (e: Exception) {
                    logger.error(e) { "Failed to read external body file: $bodyFile" }
                    return notFoundResponse("Failed to read external body file: ${e.message}")
                }
            }
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
            .setBody(bodyContent)
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

/**
 *
 */
private fun HttpFixture.fixtureKey() = fixtureKey(
    method = this.method,
    path = this.path,
    tag = this.details.tag,
    index = this.details.index,
)

/**
 * Generates a unique key for a fixture based on its method, path, index, and tag.
 *
 * Example:
 * 1. `POST /v1/chat/completions` -> `[tag]/post-chat-completions-[index].json`
 *
 * Notice that a file extension is attached to match the [FixtureDetails.filename] naming format.
 */
private fun fixtureKey(
    method: String,
    path: String,
    tag: String,
    index: Int,
): String {
    val fixtureFilename = generateFixtureFilename(method, path, fixtureIndex = index, extension = "json")
    return "$tag/$fixtureFilename"
}