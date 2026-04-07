/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.test.utils.fixtures

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import okhttp3.MediaType
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

private val logger = KotlinLogging.logger {}

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
     * @ param record The HTTP request, response, and fixture data to record
     */
    fun record(record: Record) {
        val (request, response, _) = record

        val sanitizedHeaders = sanitizer.sanitizeHeaders(response.headers)
        // resolving under: `fixtureDir/containingTestSuiteName/fixtureTag`
        val fixtureDirectory = fixturesDir
            .resolve(record.fixture.containingTestSuiteName)
            .resolve(record.fixture.tag)

        // Ensure parent directories exist
        fixtureDirectory.createDirectories()

        val mimeType = response.body.contentType?.let { "${it.type}/${it.subtype}" }

        // Determine storage strategy: inline for small JSON, external file for everything else
        val fixtureBody = if (shouldStoreInline(mimeType, bodySize = response.body.content.size)) {
            // Store inline as sanitized string
            val charset = response.body.contentType?.charset() ?: Charsets.UTF_8
            val sanitizedBody = sanitizer.sanitizeBody(
                body = response.body.content.toString(charset),
                mimeType = mimeType,
            )

            FixtureBody.Inline(sanitizedBody)
        } else {
            // Store in an external file
            val bodyFilename = generateBodyFilename(
                method = request.method,
                path = request.path,
                fixtureIndex = record.fixture.responseIndex,
                contentType = mimeType,
            )
            val bodyFile = fixtureDirectory.resolve(bodyFilename)
            bodyFile.writeBytes(response.body.content)

            logger.info { "Recorded body file: ${bodyFile.toAbsolutePath()}" }

            FixtureBody.ExternalFile(
                relativePath = bodyFilename,
                contentType = response.body.contentType?.toString(),
            )
        }

        val filename = generateFixtureFilename(
            method = request.method,
            path = request.path,
            fixtureIndex = record.fixture.responseIndex,
            extension = "json",
        )
        val fixtureFile = fixtureDirectory.resolve(filename)

        val fixture = HttpFixture(
            method = request.method,
            path = request.path,
            statusCode = response.statusCode,
            headers = sanitizedHeaders,
            body = fixtureBody,
            details = FixtureDetails(
                containingTestClassName = record.fixture.containingTestSuiteName,
                filename = filename,
                tag = record.fixture.tag,
                index = record.fixture.responseIndex,
            )
        )

        // Write fixture metadata to the JSON file
        val fixtureJson = json.encodeToString(fixture)
        fixtureFile.writeText(fixtureJson)

        logger.info { "Recorded fixture: ${fixtureFile.toAbsolutePath()}" }
    }

    /**
     * Determines whether a response body should be stored inline in the fixture JSON
     * or in an external file.
     *
     * Inline storage is used for:
     * - Small JSON responses (< 10KB)
     *
     * External storage is used for:
     * - Streaming responses (text/event-stream)
     * - Large responses (>= 10KB)
     * - Binary content (video, images, etc.)
     *
     * @param contentType The MIME type of the response
     * @param bodySize Size of the response body in bytes
     * @return true if the body should be stored inline, false for external file
     */
    private fun shouldStoreInline(contentType: String?, bodySize: Int): Boolean {
        val isJson = contentType?.startsWith("application/json", ignoreCase = true) == true
        val isSmall = bodySize < INLINE_BODY_SIZE_THRESHOLD
        return isJson && isSmall
    }

    companion object {
        /**
         * Maximum size (in bytes) for inline body storage.
         * Responses larger than this will be stored in external files.
         */
        private const val INLINE_BODY_SIZE_THRESHOLD = 10_000 // 10KB
    }
}

/**
 * Represents the body content of an HTTP fixture.
 *
 * Bodies can be stored either inline in the fixture JSON (for small, text-based responses)
 * or in external files (for large or binary responses like streaming events).
 */
@Serializable
sealed class FixtureBody {
    /**
     * Body content stored directly in the fixture JSON file.
     * Used for small text responses like JSON.
     */
    @SerialName("inline")
    @Serializable
    data class Inline(val content: String) : FixtureBody()

    /**
     * Body content stored in an external file.
     * Used for streaming responses, large responses, or binary content.
     *
     * @param relativePath Path relative to the fixture tag directory
     * @param contentType The MIME type of the content
     */
    @SerialName("external_file")
    @Serializable
    data class ExternalFile(
        val relativePath: String,
        val contentType: String? = null
    ) : FixtureBody()
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
    val body: FixtureBody,
    val details: FixtureDetails,
)

/**
 * Details about a recorded fixture.
 *
 * Example:
 * ```json
 *     "details": {
 *         "containingTestClassName": "ImagesCreateEditOpenAIApiEndpointHandlerTest",
 *         "filename": "post-responses-0.json",
 *         "tag": "user-prompt-is-sent-as-a-single-message-in-input-field",
 *         "index": 0
 *     }
 * ```
 */
@Serializable
data class FixtureDetails(
    /**
     * Class name of the test suite that contains a test case
     * dedicated to this fixture.
     */
    val containingTestClassName: String,
    /**
     * Expected to contain an extension as well (e.g., `my-file.json`)
     */
    val filename: String,
    /**
     * Usually a test case name with whitespaces replaced with dashes
     * and the leading `test` word removed.
     *
     * @see createFixtureTag
     */
    val tag: String,
    /**
     * The index of the response in the sequence of responses.
     *
     * For example, when a test case calls an API twice, there will be two responses fixtures,
     * one containing `index=0` and the other containing `index=1`.
     */
    val index: Int,
)

/**
 * Represents a recorded HTTP request, response, and associated fixture data.
 */
data class Record(
    val request: Request,
    val response: Response,
    val fixture: Fixture,
) {
    /**
     * @param method The HTTP method (GET, POST, etc.)
     * @param path The API endpoint path (e.g., "/v1/chat/completions")
     */
    data class Request(
        val method: String,
        val path: String,
    )

    /**
     * @param statusCode The HTTP status code
     * @param headers The response headers
     * @param body The response body as raw bytes
     */
    data class Response(
        val statusCode: Int,
        val headers: Map<String, List<String>>,
        val body: Body,
    ) {
        /**
         * @param contentType The MIME type of the response (e.g., "application/json", "text/event-stream")
         */
        data class Body(
            val content: ByteArray,
            val contentType: MediaType?,
        )
    }

    /**
     * @param containingTestSuiteName the name of the test suite that contains a test case
     * dedicated to this fixture.
     * @param tag tag to use as a folder where mocked responses are stored (usually, a test case name)
     * @param responseIndex the index of the response in the sequence of responses
     */
    data class Fixture(
        val containingTestSuiteName: String,
        val tag: String,
        val responseIndex: Int,
    )
}
