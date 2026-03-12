/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.core.exporters.langfuse

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.jetbrains.ai.tracy.test.utils.MediaContentAttributeValues
import org.jetbrains.ai.tracy.test.utils.createTestSpanData
import org.jetbrains.ai.tracy.test.utils.toReadableSpan
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Comprehensive tests for LangfuseMediaSpanProcessor covering the OkHttp-based media upload flow.
 *
 * The upload flow consists of 4 HTTP calls:
 * 1. POST /api/public/media - Request upload URL and media ID
 * 2. PUT to presigned URL - Upload bytes (optional, if file not already uploaded)
 * 3. PATCH /api/public/media/{mediaId} - Update upload status
 * 4. GET /api/public/media/{mediaId} - Retrieve final media data
 *
 * Tests cover both "already uploaded" (`uploadUrl` is `null`) and "upload required" (`uploadUrl` provided) paths,
 * including various error scenarios, non-2xx responses, and missing response bodies.
 */
class LangfuseMediaSpanProcessorTest {
    /**
     * The mocked version of a Langfuse server.
     * [LangfuseMediaSpanProcessor] will send its requests to this server instance.
     */
    private lateinit var langfuseServer: MockWebServer

    /**
     * The mocked version of a server that hosts media content,
     * which [LangfuseMediaSpanProcessor] will fetch media content from before uploading it to Langfuse.
     */
    private lateinit var mediaContentServer: MockWebServer
    private lateinit var processor: LangfuseMediaSpanProcessor
    private lateinit var scope: CoroutineScope

    @BeforeEach
    fun setup() {
        langfuseServer = MockWebServer()
        langfuseServer.start()

        mediaContentServer = MockWebServer()
        mediaContentServer.start()

        scope = CoroutineScope(SupervisorJob())

        val langfuseUrl = langfuseServer.url("/").toString().removeSuffix("/")
        val basicAuth = Base64.getEncoder().encodeToString("test:key".toByteArray())

        processor = LangfuseMediaSpanProcessor(
            scope = scope,
            langfuseUrl = langfuseUrl,
            langfuseBasicAuth = basicAuth
        )
    }

    @AfterEach
    fun tearDown() {
        processor.close()
        scope.cancel()
        langfuseServer.shutdown()
        mediaContentServer.shutdown()
    }

    // ========================================
    // HAPPY PATH TESTS
    // ========================================

    @Test
    fun `test upload required path - new file with uploadUrl provided`() = runTest {
        val mediaId = "test-media-id"
        val uploadUrl = langfuseServer.url("/presigned-upload").toString()

        // Step 1: POST /api/public/media - returns uploadUrl
        langfuseServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"uploadUrl": "$uploadUrl", "mediaId": "$mediaId"}""")
                .addHeader("Content-Type", "application/json")
        )

        // Step 2: PUT to presigned URL
        langfuseServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("OK")
        )

        // Step 3: PATCH /api/public/media/{mediaId}
        langfuseServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("OK")
        )

        // Step 4: GET /api/public/media/{mediaId}
        langfuseServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""
                    {
                        "mediaId": "$mediaId",
                        "contentType": "image/png",
                        "contentLength": 100,
                        "url": "https://example.com/media.png",
                        "urlExpiry": "2026-12-31T23:59:59Z",
                        "uploadedAt": "2026-03-10T10:00:00Z"
                    }
                """.trimIndent())
                .addHeader("Content-Type", "application/json")
        )

        val mediaData = "test-image-data"
        val span = createTestSpanData(
            traceId = TEST_TRACE_ID,
            mediaAttributes = listOf(
                MediaContentAttributeValues.Data(
                    field = "output",
                    contentType = "image/png",
                    data = Base64.getEncoder().encodeToString(mediaData.toByteArray())
                )
            )
        )

        processor.onEnd(span.toReadableSpan())
        delayOnDefault(500)

        // Verify all 4 requests were made
        assertEquals(4, langfuseServer.requestCount)

        val postRequest = langfuseServer.takeRequest()
        assertEquals("/api/public/media", postRequest.path)
        assertEquals("POST", postRequest.method)
        assertTrue(postRequest.body.readUtf8().contains(TEST_TRACE_ID))

        val putRequest = langfuseServer.takeRequest()
        assertEquals("/presigned-upload", putRequest.path)
        assertEquals("PUT", putRequest.method)

        val patchRequest = langfuseServer.takeRequest()
        assertEquals("/api/public/media/${mediaId}", patchRequest.path)
        assertEquals("PATCH", patchRequest.method)

        val getRequest = langfuseServer.takeRequest()
        assertEquals("/api/public/media/${mediaId}", getRequest.path)
        assertEquals("GET", getRequest.method)
    }

    @Test
    fun `test already uploaded path - uploadUrl is null`() = runTest {
        val mediaId = "existing-media-id"

        // Step 1: POST /api/public/media - returns null uploadUrl (already uploaded)
        langfuseServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"uploadUrl": null, "mediaId": "$mediaId"}""")
                .addHeader("Content-Type", "application/json")
        )

        // Step 2: GET /api/public/media/{mediaId} (PUT/PATCH skipped)
        langfuseServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""
                    {
                        "mediaId": "$mediaId",
                        "contentType": "image/jpeg",
                        "contentLength": 200,
                        "url": "https://example.com/existing.jpg",
                        "urlExpiry": "2026-12-31T23:59:59Z",
                        "uploadedAt": "2026-03-09T10:00:00Z"
                    }
                """.trimIndent())
                .addHeader("Content-Type", "application/json")
        )

        val span = createTestSpanData(
            traceId = TEST_TRACE_ID,
            mediaAttributes = listOf(
                MediaContentAttributeValues.Data(
                    field = "input",
                    contentType = "image/jpeg",
                    data = Base64.getEncoder().encodeToString("existing-image-data".toByteArray())
                )
            )
        )

        processor.onEnd(span.toReadableSpan())
        delayOnDefault(500)

        // Verify only POST and GET were made (no PUT/PATCH)
        assertEquals(2, langfuseServer.requestCount)

        val postRequest = langfuseServer.takeRequest()
        assertEquals("/api/public/media", postRequest.path)
        assertEquals("POST", postRequest.method)

        val getRequest = langfuseServer.takeRequest()
        assertEquals("/api/public/media/${mediaId}", getRequest.path)
        assertEquals("GET", getRequest.method)
    }

    @Test
    fun `test upload from external URL source`() = runTest {
        val mediaId = "url-media-id"
        val uploadUrl = langfuseServer.url("/presigned-upload").toString()
        val externalUrl = mediaContentServer.url("/external-image.jpg").toString()

        // External server returns image
        mediaContentServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("external-image-bytes")
                .addHeader("Content-Type", "image/jpeg")
        )

        // Langfuse flow: POST, PUT, PATCH, GET
        langfuseServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"uploadUrl": "$uploadUrl", "mediaId": "$mediaId"}""")
                .addHeader("Content-Type", "application/json")
        )

        langfuseServer.enqueue(MockResponse().setResponseCode(200).setBody("OK"))
        langfuseServer.enqueue(MockResponse().setResponseCode(200).setBody("OK"))

        langfuseServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""
                    {
                        "mediaId": "$mediaId",
                        "contentType": "image/jpeg",
                        "contentLength": 300,
                        "url": "https://example.com/url-media.jpg",
                        "urlExpiry": "2026-12-31T23:59:59Z",
                        "uploadedAt": "2026-03-10T10:00:00Z"
                    }
                """.trimIndent())
                .addHeader("Content-Type", "application/json")
        )

        val span = createTestSpanData(
            traceId = TEST_TRACE_ID,
            mediaAttributes = listOf(
                MediaContentAttributeValues.Url(
                    field = "input",
                    url = externalUrl
                )
            )
        )

        processor.onEnd(span.toReadableSpan())
        delayOnDefault(500)

        // Verify external URL was fetched
        assertEquals(1, mediaContentServer.requestCount)
        val externalRequest = mediaContentServer.takeRequest()
        assertEquals("/external-image.jpg", externalRequest.path)
        assertEquals("GET", externalRequest.method)

        // Verify Langfuse flow completed
        assertEquals(4, langfuseServer.requestCount)
    }

    // ========================================
    // POST /api/public/media ERROR TESTS
    // ========================================

    @Test
    fun `test POST returns 401 unauthorized`() = runTest {
        langfuseServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("Unauthorized")
        )

        val span = createTestSpanData(
            traceId = TEST_TRACE_ID,
            mediaAttributes = listOf(
                MediaContentAttributeValues.Data(
                    field = "output",
                    contentType = "image/png",
                    data = Base64.getEncoder().encodeToString("test".toByteArray())
                )
            )
        )

        processor.onEnd(span.toReadableSpan())
        delayOnDefault(500)

        // Only POST request should be made
        assertEquals(1, langfuseServer.requestCount)

        val postRequest = langfuseServer.takeRequest()
        assertEquals("/api/public/media", postRequest.path)
        assertEquals("POST", postRequest.method)
    }

    @Test
    fun `test POST returns unparseable JSON`() = runTest {
        langfuseServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("invalid json {{{")
                .addHeader("Content-Type", "application/json")
        )

        val span = createTestSpanData(
            traceId = TEST_TRACE_ID,
            mediaAttributes = listOf(
                MediaContentAttributeValues.Data(
                    field = "output",
                    contentType = "image/png",
                    data = Base64.getEncoder().encodeToString("test".toByteArray())
                )
            )
        )

        processor.onEnd(span.toReadableSpan())
        delayOnDefault(500)

        assertEquals(1, langfuseServer.requestCount)

        val postRequest = langfuseServer.takeRequest()
        assertEquals("/api/public/media", postRequest.path)
        assertEquals("POST", postRequest.method)
    }

    @Test
    fun `test invalid content type format`() = runTest {
        val span = createTestSpanData(
            traceId = TEST_TRACE_ID,
            mediaAttributes = listOf(
                MediaContentAttributeValues.Data(
                    field = "output",
                    contentType = "invalid/content/type/format/",
                    data = Base64.getEncoder().encodeToString("test".toByteArray())
                )
            )
        )

        processor.onEnd(span.toReadableSpan())
        delayOnDefault(500)

        // Should fail validation before POST
        assertEquals(0, langfuseServer.requestCount)
    }

    // ========================================
    // PUT PRESIGNED URL ERROR TESTS
    // ========================================

    @Test
    fun `test PUT to presigned URL fails with 403`() = runTest {
        val mediaId = "test-media-id"
        val uploadUrl = langfuseServer.url("/presigned-upload").toString()

        langfuseServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"uploadUrl": "$uploadUrl", "mediaId": "$mediaId"}""")
                .addHeader("Content-Type", "application/json")
        )

        // PUT fails
        langfuseServer.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setBody("Forbidden")
        )

        // PATCH should still be called to report the error
        langfuseServer.enqueue(MockResponse().setResponseCode(200).setBody("OK"))

        // GET request still will be called
        langfuseServer.enqueue(MockResponse()
            .setResponseCode(404)
            .setBody("Not Found")
        )

        val span = createTestSpanData(
            traceId = TEST_TRACE_ID,
            mediaAttributes = listOf(
                MediaContentAttributeValues.Data(
                    field = "output",
                    contentType = "image/png",
                    data = Base64.getEncoder().encodeToString("test".toByteArray())
                )
            )
        )

        processor.onEnd(span.toReadableSpan())
        delayOnDefault(500)

        // POST, PUT, PATCH, and GET should be made (GET continues even after the PUT error)
        assertEquals(4, langfuseServer.requestCount)

        langfuseServer.takeRequest() // POST
        langfuseServer.takeRequest() // PUT

        val patchRequest = langfuseServer.takeRequest() // PATCH
        val patchBody = patchRequest.body.readUtf8()
        assertTrue(patchBody.contains("403"))
    }

    @Test
    fun `test PUT returns 500 server error`() = runTest {
        val mediaId = "test-media-id"
        val uploadUrl = langfuseServer.url("/presigned-upload").toString()

        langfuseServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"uploadUrl": "$uploadUrl", "mediaId": "$mediaId"}""")
                .addHeader("Content-Type", "application/json")
        )

        langfuseServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error")
        )

        langfuseServer.enqueue(MockResponse().setResponseCode(200).setBody("OK"))

        // GET request still will be called
        langfuseServer.enqueue(MockResponse()
            .setResponseCode(404)
            .setBody("Not Found")
        )

        val span = createTestSpanData(
            traceId = TEST_TRACE_ID,
            mediaAttributes = listOf(
                MediaContentAttributeValues.Data(
                    field = "output",
                    contentType = "image/png",
                    data = Base64.getEncoder().encodeToString("test".toByteArray())
                )
            )
        )

        processor.onEnd(span.toReadableSpan())
        delayOnDefault(500)

        // POST, PUT, PATCH, and GET should be made (GET continues even after PUT error)
        assertEquals(4, langfuseServer.requestCount)

        langfuseServer.takeRequest() // POST
        langfuseServer.takeRequest() // PUT

        val patchRequest = langfuseServer.takeRequest() // PATCH
        val patchBody = patchRequest.body.readUtf8()
        // the patch request body should indicate the response code of the PUT request
        assertTrue(patchBody.contains("500"))
    }

    // ========================================
    // PATCH STATUS UPDATE ERROR TESTS
    // ========================================

    @Test
    fun `test PATCH status update fails with 404`() = runTest {
        val mediaId = "test-media-id"
        val uploadUrl = langfuseServer.url("/presigned-upload").toString()

        langfuseServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"uploadUrl": "$uploadUrl", "mediaId": "$mediaId"}""")
                .addHeader("Content-Type", "application/json")
        )

        langfuseServer.enqueue(MockResponse().setResponseCode(200).setBody("OK"))

        // PATCH fails
        langfuseServer.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("Media not found")
        )

        // GET request still will be called
        langfuseServer.enqueue(MockResponse()
            .setResponseCode(404)
            .setBody("Not Found")
        )

        val span = createTestSpanData(
            traceId = TEST_TRACE_ID,
            mediaAttributes = listOf(
                MediaContentAttributeValues.Data(
                    field = "output",
                    contentType = "image/png",
                    data = Base64.getEncoder().encodeToString("test".toByteArray())
                )
            )
        )

        processor.onEnd(span.toReadableSpan())
        delayOnDefault(500)

        // POST, PUT, PATCH, and GET should be made (GET continues even after PATCH error)
        assertEquals(4, langfuseServer.requestCount)
    }

    @Test
    fun `test PATCH fails but PUT succeeded - error aggregation`() = runTest {
        val mediaId = "test-media-id"
        val uploadUrl = langfuseServer.url("/presigned-upload").toString()

        langfuseServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"uploadUrl": "$uploadUrl", "mediaId": "$mediaId"}""")
                .addHeader("Content-Type", "application/json")
        )

        // PUT succeeds
        langfuseServer.enqueue(MockResponse().setResponseCode(200).setBody("OK"))

        // PATCH fails
        langfuseServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Server error")
        )

        // GET request still will be called
        langfuseServer.enqueue(MockResponse()
            .setResponseCode(404)
            .setBody("Not Found")
        )

        val span = createTestSpanData(
            traceId = TEST_TRACE_ID,
            mediaAttributes = listOf(
                MediaContentAttributeValues.Data(
                    field = "output",
                    contentType = "image/png",
                    data = Base64.getEncoder().encodeToString("test".toByteArray())
                )
            )
        )

        processor.onEnd(span.toReadableSpan())
        delayOnDefault(500)

        // POST, PUT, PATCH, and GET should be made (GET continues even after PATCH error)
        assertEquals(4, langfuseServer.requestCount)

        langfuseServer.takeRequest() // POST
        langfuseServer.takeRequest() // PUT

        val patchRequest = langfuseServer.takeRequest() // PATCH
        val patchBody = patchRequest.body.readUtf8()
        // PATCH body should show successful PUT status (200)
        assertTrue(patchBody.contains("200"))
    }

    // ========================================
    // GET MEDIA DATA ERROR TESTS
    // ========================================

    @Test
    fun `test GET media data returns 404`() = runTest {
        val mediaId = "test-media-id"

        langfuseServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"uploadUrl": null, "mediaId": "$mediaId"}""")
                .addHeader("Content-Type", "application/json")
        )

        // GET fails
        langfuseServer.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("Media not found")
        )

        val span = createTestSpanData(
            traceId = TEST_TRACE_ID,
            mediaAttributes = listOf(
                MediaContentAttributeValues.Data(
                    field = "output",
                    contentType = "image/png",
                    data = Base64.getEncoder().encodeToString("test".toByteArray())
                )
            )
        )

        processor.onEnd(span.toReadableSpan())
        delayOnDefault(500)

        assertEquals(2, langfuseServer.requestCount)
    }

    @Test
    fun `test GET media data returns unparseable JSON`() = runTest {
        val mediaId = "test-media-id"

        langfuseServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"uploadUrl": null, "mediaId": "$mediaId"}""")
                .addHeader("Content-Type", "application/json")
        )

        langfuseServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("not valid json !!!")
                .addHeader("Content-Type", "application/json")
        )

        val span = createTestSpanData(
            traceId = TEST_TRACE_ID,
            mediaAttributes = listOf(
                MediaContentAttributeValues.Data(
                    field = "output",
                    contentType = "image/png",
                    data = Base64.getEncoder().encodeToString("test".toByteArray())
                )
            )
        )

        processor.onEnd(span.toReadableSpan())
        delayOnDefault(500)

        assertEquals(2, langfuseServer.requestCount)
    }

    // ========================================
    // URL SOURCE ERROR TESTS
    // ========================================

    @Test
    fun `test external URL returns 404`() = runTest {
        val externalUrl = mediaContentServer.url("/missing-image.jpg").toString()

        mediaContentServer.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("Not found")
        )

        val span = createTestSpanData(
            traceId = TEST_TRACE_ID,
            mediaAttributes = listOf(
                MediaContentAttributeValues.Url(
                    field = "input",
                    url = externalUrl
                )
            )
        )

        processor.onEnd(span.toReadableSpan())
        delayOnDefault(500)

        // Only external request should be made
        assertEquals(1, mediaContentServer.requestCount)
        assertEquals(0, langfuseServer.requestCount)
    }

    @Test
    fun `test external URL missing Content-Type header`() = runTest {
        val externalUrl = mediaContentServer.url("/no-content-type.jpg").toString()

        mediaContentServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("image-data")
                // Missing Content-Type header
        )

        val span = createTestSpanData(
            traceId = TEST_TRACE_ID,
            mediaAttributes = listOf(
                MediaContentAttributeValues.Url(
                    field = "input",
                    url = externalUrl
                )
            )
        )

        processor.onEnd(span.toReadableSpan())
        delayOnDefault(500)

        assertEquals(1, mediaContentServer.requestCount)
        assertEquals(0, langfuseServer.requestCount)
    }

    // ========================================
    // BASE64 AND EDGE CASE TESTS
    // ========================================

    @Test
    fun `test invalid Base64 data`() = runTest {
        val span = createTestSpanData(
            traceId = TEST_TRACE_ID,
            mediaAttributes = listOf(
                MediaContentAttributeValues.Data(
                    field = "output",
                    contentType = "image/png",
                    data = "!!!invalid-base64-data!!!"
                )
            )
        )

        processor.onEnd(span.toReadableSpan())
        delayOnDefault(500)

        // Should fail before making any requests
        assertEquals(0, langfuseServer.requestCount)
    }

    @Test
    fun `test graceful shutdown waits for active uploads`() = runTest {
        val mediaId = "test-media-id"
        val uploadUrl = langfuseServer.url("/presigned-upload").toString()

        // Slow responses to simulate long-running upload
        langfuseServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"uploadUrl": "$uploadUrl", "mediaId": "$mediaId"}""")
                .addHeader("Content-Type", "application/json")
                .setBodyDelay(100, TimeUnit.MILLISECONDS)
        )

        langfuseServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("OK")
                .setBodyDelay(100, TimeUnit.MILLISECONDS)
        )

        langfuseServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("OK")
                .setBodyDelay(100, TimeUnit.MILLISECONDS)
        )

        langfuseServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""
                    {
                        "mediaId": "$mediaId",
                        "contentType": "image/png",
                        "contentLength": 100,
                        "url": "https://example.com/media.png",
                        "urlExpiry": "2026-12-31T23:59:59Z",
                        "uploadedAt": "2026-03-10T10:00:00Z"
                    }
                """.trimIndent())
                .addHeader("Content-Type", "application/json")
                .setBodyDelay(100, TimeUnit.MILLISECONDS)
        )

        val span = createTestSpanData(
            traceId = TEST_TRACE_ID,
            mediaAttributes = listOf(
                MediaContentAttributeValues.Data(
                    field = "output",
                    contentType = "image/png",
                    data = Base64.getEncoder().encodeToString("test".toByteArray())
                )
            )
        )

        processor.onEnd(span.toReadableSpan())

        // Immediately trigger shutdown
        val shutdownResult = processor.shutdown()

        // Shutdown should wait for upload to complete
        shutdownResult.join(2000, TimeUnit.MILLISECONDS)

        assertTrue(shutdownResult.isSuccess)
        // All requests should have completed
        assertEquals(4, langfuseServer.requestCount)
    }

    companion object {
        private const val TEST_TRACE_ID = "00000000000000000000000000000001"

        private suspend fun delayOnDefault(timeMillis: Long) = withContext(Dispatchers.Default) {
            delay(timeMillis)
        }
    }
}