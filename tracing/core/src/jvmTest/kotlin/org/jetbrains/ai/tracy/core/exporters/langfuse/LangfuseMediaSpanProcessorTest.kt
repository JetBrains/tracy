/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.core.exporters.langfuse

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo
import io.opentelemetry.sdk.common.InstrumentationScopeInfo
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.testing.trace.TestSpanData
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.data.StatusData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.jetbrains.ai.tracy.core.adapters.media.SupportedMediaContentTypes
import org.jetbrains.ai.tracy.core.adapters.media.UploadableMediaContentAttributeKeys
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*
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
 * Tests cover both "already uploaded" (uploadUrl is null) and "upload required" (uploadUrl provided) paths,
 * including various error scenarios, non-2xx responses, and missing response bodies.
 */
class LangfuseMediaSpanProcessorTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var mockExternalServer: MockWebServer
    private lateinit var processor: LangfuseMediaSpanProcessor
    private lateinit var scope: CoroutineScope

    @BeforeEach
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        mockExternalServer = MockWebServer()
        mockExternalServer.start()

        scope = CoroutineScope(SupervisorJob())

        val langfuseUrl = mockWebServer.url("/").toString().removeSuffix("/")
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
        mockWebServer.shutdown()
        mockExternalServer.shutdown()
    }

    // ========================================
    // HAPPY PATH TESTS
    // ========================================

    @Test
    fun `test upload required path - new file with uploadUrl provided`() = runTest {
        val mediaId = "test-media-id"
        val uploadUrl = mockWebServer.url("/presigned-upload").toString()

        // Step 1: POST /api/public/media - returns uploadUrl
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"uploadUrl": "$uploadUrl", "mediaId": "$mediaId"}""")
                .addHeader("Content-Type", "application/json")
        )

        // Step 2: PUT to presigned URL
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("OK")
        )

        // Step 3: PATCH /api/public/media/{mediaId}
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("OK")
        )

        // Step 4: GET /api/public/media/{mediaId}
        mockWebServer.enqueue(
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
        val span = createTestSpan(
            traceId = TEST_TRACE_ID,
            mediaAttributes = listOf(
                MediaAttributes(
                    index = 0,
                    type = SupportedMediaContentTypes.BASE64.type,
                    field = "output",
                    contentType = "image/png",
                    data = Base64.getEncoder().encodeToString(mediaData.toByteArray())
                )
            )
        )

        processor.onEnd(span.toReadableSpan())
        Thread.sleep(1000)

        // Verify all 4 requests were made
        assertEquals(4, mockWebServer.requestCount)

        val postRequest = mockWebServer.takeRequest()
        assertEquals("/api/public/media", postRequest.path)
        assertEquals("POST", postRequest.method)
        assertTrue(postRequest.body.readUtf8().contains(TEST_TRACE_ID))

        val putRequest = mockWebServer.takeRequest()
        assertEquals("/presigned-upload", putRequest.path)
        assertEquals("PUT", putRequest.method)

        val patchRequest = mockWebServer.takeRequest()
        assertEquals("/api/public/media/${mediaId}", patchRequest.path)
        assertEquals("PATCH", patchRequest.method)

        val getRequest = mockWebServer.takeRequest()
        assertEquals("/api/public/media/${mediaId}", getRequest.path)
        assertEquals("GET", getRequest.method)
    }

    @Test
    fun `test already uploaded path - uploadUrl is null`() = runTest {
        val mediaId = "existing-media-id"

        // Step 1: POST /api/public/media - returns null uploadUrl (already uploaded)
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"uploadUrl": null, "mediaId": "$mediaId"}""")
                .addHeader("Content-Type", "application/json")
        )

        // Step 2: GET /api/public/media/{mediaId} (PUT/PATCH skipped)
        mockWebServer.enqueue(
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

        val span = createTestSpan(
            traceId = TEST_TRACE_ID,
            mediaAttributes = listOf(
                MediaAttributes(
                    index = 0,
                    type = SupportedMediaContentTypes.BASE64.type,
                    field = "input",
                    contentType = "image/jpeg",
                    data = Base64.getEncoder().encodeToString("existing-image-data".toByteArray())
                )
            )
        )

        processor.onEnd(span.toReadableSpan())
        delay(500)

        // Verify only POST and GET were made (no PUT/PATCH)
        assertEquals(2, mockWebServer.requestCount)

        val postRequest = mockWebServer.takeRequest()
        assertEquals("/api/public/media", postRequest.path)
        assertEquals("POST", postRequest.method)

        val getRequest = mockWebServer.takeRequest()
        assertTrue(getRequest.path!!.startsWith("/api/public/media/"))
        assertEquals("GET", getRequest.method)
    }

    @Test
    fun `test upload from external URL source`() = runTest {
        val mediaId = "url-media-id"
        val uploadUrl = mockWebServer.url("/presigned-upload").toString()
        val externalUrl = mockExternalServer.url("/external-image.jpg").toString()

        // External server returns image
        mockExternalServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("external-image-bytes")
                .addHeader("Content-Type", "image/jpeg")
        )

        // Langfuse flow: POST, PUT, PATCH, GET
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"uploadUrl": "$uploadUrl", "mediaId": "$mediaId"}""")
                .addHeader("Content-Type", "application/json")
        )

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("OK"))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("OK"))

        mockWebServer.enqueue(
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

        val span = createTestSpan(
            traceId = TEST_TRACE_ID,
            mediaAttributes = listOf(
                MediaAttributes(
                    index = 0,
                    type = SupportedMediaContentTypes.URL.type,
                    field = "input",
                    url = externalUrl
                )
            )
        )

        processor.onEnd(span.toReadableSpan())
        delay(500)

        // Verify external URL was fetched
        assertEquals(1, mockExternalServer.requestCount)
        val externalRequest = mockExternalServer.takeRequest()
        assertEquals("/external-image.jpg", externalRequest.path)
        assertEquals("GET", externalRequest.method)

        // Verify Langfuse flow completed
        assertEquals(4, mockWebServer.requestCount)
    }

    @Test
    fun `test multiple media items in single span`() = runTest {
        val mediaId1 = "media-id-1"
        val mediaId2 = "media-id-2"
        val uploadUrl1 = mockWebServer.url("/presigned-1").toString()
        val uploadUrl2 = mockWebServer.url("/presigned-2").toString()

        // First media item: POST, PUT, PATCH, GET
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"uploadUrl": "$uploadUrl1", "mediaId": "$mediaId1"}""")
                .addHeader("Content-Type", "application/json")
        )
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("OK"))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("OK"))
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""
                    {
                        "mediaId": "$mediaId1",
                        "contentType": "image/png",
                        "contentLength": 100,
                        "url": "https://example.com/media1.png",
                        "urlExpiry": "2026-12-31T23:59:59Z",
                        "uploadedAt": "2026-03-10T10:00:00Z"
                    }
                """.trimIndent())
                .addHeader("Content-Type", "application/json")
        )

        // Second media item: POST, PUT, PATCH, GET
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"uploadUrl": "$uploadUrl2", "mediaId": "$mediaId2"}""")
                .addHeader("Content-Type", "application/json")
        )
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("OK"))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("OK"))
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""
                    {
                        "mediaId": "$mediaId2",
                        "contentType": "audio/mp3",
                        "contentLength": 200,
                        "url": "https://example.com/audio.mp3",
                        "urlExpiry": "2026-12-31T23:59:59Z",
                        "uploadedAt": "2026-03-10T10:00:00Z"
                    }
                """.trimIndent())
                .addHeader("Content-Type", "application/json")
        )

        val span = createTestSpan(
            traceId = TEST_TRACE_ID,
            mediaAttributes = listOf(
                MediaAttributes(
                    index = 0,
                    type = SupportedMediaContentTypes.BASE64.type,
                    field = "input",
                    contentType = "image/png",
                    data = Base64.getEncoder().encodeToString("image-data".toByteArray())
                ),
                MediaAttributes(
                    index = 1,
                    type = SupportedMediaContentTypes.BASE64.type,
                    field = "output",
                    contentType = "audio/mp3",
                    data = Base64.getEncoder().encodeToString("audio-data".toByteArray())
                )
            )
        )

        processor.onEnd(span.toReadableSpan())
        delay(1000)

        // Should have 8 requests total (4 per media item)
        assertEquals(8, mockWebServer.requestCount)
    }

    // ========================================
    // POST /api/public/media ERROR TESTS
    // ========================================

    @Test
    fun `test POST returns 401 unauthorized`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("Unauthorized")
        )

        val span = createTestSpan(
            traceId = TEST_TRACE_ID,
            mediaAttributes = listOf(
                MediaAttributes(
                    index = 0,
                    type = SupportedMediaContentTypes.BASE64.type,
                    field = "output",
                    contentType = "image/png",
                    data = Base64.getEncoder().encodeToString("test".toByteArray())
                )
            )
        )

        processor.onEnd(span.toReadableSpan())
        delay(500)

        // Only POST request should be made
        assertEquals(1, mockWebServer.requestCount)
    }

    @Test
    fun `test POST returns unparseable JSON`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("invalid json {{{")
                .addHeader("Content-Type", "application/json")
        )

        val span = createTestSpan(
            traceId = TEST_TRACE_ID,
            mediaAttributes = listOf(
                MediaAttributes(
                    index = 0,
                    type = SupportedMediaContentTypes.BASE64.type,
                    field = "output",
                    contentType = "image/png",
                    data = Base64.getEncoder().encodeToString("test".toByteArray())
                )
            )
        )

        processor.onEnd(span.toReadableSpan())
        delay(500)

        assertEquals(1, mockWebServer.requestCount)
    }

    @Test
    fun `test invalid content type format`() = runTest {
        val span = createTestSpan(
            traceId = TEST_TRACE_ID,
            mediaAttributes = listOf(
                MediaAttributes(
                    index = 0,
                    type = SupportedMediaContentTypes.BASE64.type,
                    field = "output",
                    contentType = "invalid/content/type/format/",
                    data = Base64.getEncoder().encodeToString("test".toByteArray())
                )
            )
        )

        processor.onEnd(span.toReadableSpan())
        delay(500)

        // Should fail validation before POST
        assertEquals(0, mockWebServer.requestCount)
    }

    // ========================================
    // PUT PRESIGNED URL ERROR TESTS
    // ========================================

    @Test
    fun `test PUT to presigned URL fails with 403`() = runTest {
        val mediaId = "test-media-id"
        val uploadUrl = mockWebServer.url("/presigned-upload").toString()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"uploadUrl": "$uploadUrl", "mediaId": "$mediaId"}""")
                .addHeader("Content-Type", "application/json")
        )

        // PUT fails
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setBody("Forbidden")
        )

        // PATCH should still be called to report the error
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("OK"))

        val span = createTestSpan(
            traceId = TEST_TRACE_ID,
            mediaAttributes = listOf(
                MediaAttributes(
                    index = 0,
                    type = SupportedMediaContentTypes.BASE64.type,
                    field = "output",
                    contentType = "image/png",
                    data = Base64.getEncoder().encodeToString("test".toByteArray())
                )
            )
        )

        processor.onEnd(span.toReadableSpan())
        delay(500)

        // POST, PUT, and PATCH should be made
        assertEquals(3, mockWebServer.requestCount)

        mockWebServer.takeRequest() // POST
        mockWebServer.takeRequest() // PUT

        val patchRequest = mockWebServer.takeRequest() // PATCH
        val patchBody = patchRequest.body.readUtf8()
        assertTrue(patchBody.contains("403"))
    }

    @Test
    fun `test PUT returns 500 server error`() = runTest {
        val mediaId = "test-media-id"
        val uploadUrl = mockWebServer.url("/presigned-upload").toString()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"uploadUrl": "$uploadUrl", "mediaId": "$mediaId"}""")
                .addHeader("Content-Type", "application/json")
        )

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error")
        )

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("OK"))

        val span = createTestSpan(
            traceId = TEST_TRACE_ID,
            mediaAttributes = listOf(
                MediaAttributes(
                    index = 0,
                    type = SupportedMediaContentTypes.BASE64.type,
                    field = "output",
                    contentType = "image/png",
                    data = Base64.getEncoder().encodeToString("test".toByteArray())
                )
            )
        )

        processor.onEnd(span.toReadableSpan())
        delay(500)

        assertEquals(3, mockWebServer.requestCount)

        mockWebServer.takeRequest() // POST
        mockWebServer.takeRequest() // PUT

        val patchRequest = mockWebServer.takeRequest() // PATCH
        val patchBody = patchRequest.body.readUtf8()
        assertTrue(patchBody.contains("500"))
    }

    // ========================================
    // PATCH STATUS UPDATE ERROR TESTS
    // ========================================

    @Test
    fun `test PATCH status update fails with 404`() = runTest {
        val mediaId = "test-media-id"
        val uploadUrl = mockWebServer.url("/presigned-upload").toString()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"uploadUrl": "$uploadUrl", "mediaId": "$mediaId"}""")
                .addHeader("Content-Type", "application/json")
        )

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("OK"))

        // PATCH fails
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("Media not found")
        )

        val span = createTestSpan(
            traceId = TEST_TRACE_ID,
            mediaAttributes = listOf(
                MediaAttributes(
                    index = 0,
                    type = SupportedMediaContentTypes.BASE64.type,
                    field = "output",
                    contentType = "image/png",
                    data = Base64.getEncoder().encodeToString("test".toByteArray())
                )
            )
        )

        processor.onEnd(span.toReadableSpan())
        delay(500)

        assertEquals(3, mockWebServer.requestCount)
    }

    @Test
    fun `test PATCH fails but PUT succeeded - error aggregation`() = runTest {
        val mediaId = "test-media-id"
        val uploadUrl = mockWebServer.url("/presigned-upload").toString()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"uploadUrl": "$uploadUrl", "mediaId": "$mediaId"}""")
                .addHeader("Content-Type", "application/json")
        )

        // PUT succeeds
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("OK"))

        // PATCH fails
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Server error")
        )

        val span = createTestSpan(
            traceId = TEST_TRACE_ID,
            mediaAttributes = listOf(
                MediaAttributes(
                    index = 0,
                    type = SupportedMediaContentTypes.BASE64.type,
                    field = "output",
                    contentType = "image/png",
                    data = Base64.getEncoder().encodeToString("test".toByteArray())
                )
            )
        )

        processor.onEnd(span.toReadableSpan())
        delay(500)

        assertEquals(3, mockWebServer.requestCount)

        mockWebServer.takeRequest() // POST
        mockWebServer.takeRequest() // PUT

        val patchRequest = mockWebServer.takeRequest() // PATCH
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

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"uploadUrl": null, "mediaId": "$mediaId"}""")
                .addHeader("Content-Type", "application/json")
        )

        // GET fails
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("Media not found")
        )

        val span = createTestSpan(
            traceId = TEST_TRACE_ID,
            mediaAttributes = listOf(
                MediaAttributes(
                    index = 0,
                    type = SupportedMediaContentTypes.BASE64.type,
                    field = "output",
                    contentType = "image/png",
                    data = Base64.getEncoder().encodeToString("test".toByteArray())
                )
            )
        )

        processor.onEnd(span.toReadableSpan())
        delay(500)

        assertEquals(2, mockWebServer.requestCount)
    }

    @Test
    fun `test GET media data returns unparseable JSON`() = runTest {
        val mediaId = "test-media-id"

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"uploadUrl": null, "mediaId": "$mediaId"}""")
                .addHeader("Content-Type", "application/json")
        )

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("not valid json !!!")
                .addHeader("Content-Type", "application/json")
        )

        val span = createTestSpan(
            traceId = TEST_TRACE_ID,
            mediaAttributes = listOf(
                MediaAttributes(
                    index = 0,
                    type = SupportedMediaContentTypes.BASE64.type,
                    field = "output",
                    contentType = "image/png",
                    data = Base64.getEncoder().encodeToString("test".toByteArray())
                )
            )
        )

        processor.onEnd(span.toReadableSpan())
        delay(500)

        assertEquals(2, mockWebServer.requestCount)
    }

    // ========================================
    // URL SOURCE ERROR TESTS
    // ========================================

    @Test
    fun `test external URL returns 404`() = runTest {
        val externalUrl = mockExternalServer.url("/missing-image.jpg").toString()

        mockExternalServer.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("Not found")
        )

        val span = createTestSpan(
            traceId = TEST_TRACE_ID,
            mediaAttributes = listOf(
                MediaAttributes(
                    index = 0,
                    type = SupportedMediaContentTypes.URL.type,
                    field = "input",
                    url = externalUrl
                )
            )
        )

        processor.onEnd(span.toReadableSpan())
        delay(500)

        // Only external request should be made
        assertEquals(1, mockExternalServer.requestCount)
        assertEquals(0, mockWebServer.requestCount)
    }

    @Test
    fun `test external URL missing Content-Type header`() = runTest {
        val externalUrl = mockExternalServer.url("/no-content-type.jpg").toString()

        mockExternalServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("image-data")
                // Missing Content-Type header
        )

        val span = createTestSpan(
            traceId = TEST_TRACE_ID,
            mediaAttributes = listOf(
                MediaAttributes(
                    index = 0,
                    type = SupportedMediaContentTypes.URL.type,
                    field = "input",
                    url = externalUrl
                )
            )
        )

        processor.onEnd(span.toReadableSpan())
        delay(500)

        assertEquals(1, mockExternalServer.requestCount)
        assertEquals(0, mockWebServer.requestCount)
    }

    // ========================================
    // BASE64 AND EDGE CASE TESTS
    // ========================================

    @Test
    fun `test invalid Base64 data`() = runTest {
        val span = createTestSpan(
            traceId = TEST_TRACE_ID,
            mediaAttributes = listOf(
                MediaAttributes(
                    index = 0,
                    type = SupportedMediaContentTypes.BASE64.type,
                    field = "output",
                    contentType = "image/png",
                    data = "!!!invalid-base64-data!!!"
                )
            )
        )

        processor.onEnd(span.toReadableSpan())
        delay(500)

        // Should fail before making any requests
        assertEquals(0, mockWebServer.requestCount)
    }

    @Test
    fun `test graceful shutdown waits for active uploads`() = runTest {
        val mediaId = "test-media-id"
        val uploadUrl = mockWebServer.url("/presigned-upload").toString()

        // Slow responses to simulate long-running upload
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"uploadUrl": "$uploadUrl", "mediaId": "$mediaId"}""")
                .addHeader("Content-Type", "application/json")
                .setBodyDelay(100, TimeUnit.MILLISECONDS)
        )

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("OK")
                .setBodyDelay(100, TimeUnit.MILLISECONDS)
        )

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("OK")
                .setBodyDelay(100, TimeUnit.MILLISECONDS)
        )

        mockWebServer.enqueue(
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

        val span = createTestSpan(
            traceId = TEST_TRACE_ID,
            mediaAttributes = listOf(
                MediaAttributes(
                    index = 0,
                    type = SupportedMediaContentTypes.BASE64.type,
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
        assertEquals(4, mockWebServer.requestCount)
    }

    // ========================================
    // HELPER FUNCTIONS
    // ========================================

    private fun createTestSpan(
        traceId: String,
        mediaAttributes: List<MediaAttributes>
    ): SpanData {
        val attributesBuilder = Attributes.builder()

        mediaAttributes.forEach { media ->
            val keys = UploadableMediaContentAttributeKeys.forIndex(media.index)
            attributesBuilder.put(keys.type, media.type)
            attributesBuilder.put(keys.field, media.field)

            when (media.type) {
                SupportedMediaContentTypes.URL.type -> {
                    attributesBuilder.put(keys.url, media.url!!)
                }
                SupportedMediaContentTypes.BASE64.type -> {
                    attributesBuilder.put(keys.contentType, media.contentType!!)
                    attributesBuilder.put(keys.data, media.data!!)
                }
            }
        }

        return TestSpanData.builder()
            .setName("test-span")
            .setKind(SpanKind.INTERNAL)
            .setSpanContext(
                SpanContext.create(
                    traceId,
                    "0000000000000001",
                    TraceFlags.getSampled(),
                    TraceState.getDefault()
                )
            )
            .setStartEpochNanos(System.nanoTime())
            .setEndEpochNanos(System.nanoTime())
            .setStatus(StatusData.ok())
            .setHasEnded(true)
            .setAttributes(attributesBuilder.build())
            .setTotalAttributeCount(attributesBuilder.build().size())
            .setEvents(emptyList())
            .setLinks(emptyList())
            .setResource(Resource.getDefault())
            .setInstrumentationScopeInfo(InstrumentationScopeInfo.empty())
            .build()
    }

    private fun SpanData.toReadableSpan(): ReadableSpan = SpanDataAdapter(this)

    private class SpanDataAdapter(private val span: SpanData): ReadableSpan {
        override fun getSpanContext(): SpanContext = span.spanContext
        override fun getParentSpanContext(): SpanContext = span.parentSpanContext
        override fun getName(): String = span.name
        override fun toSpanData() = span
        @Deprecated("Deprecated in Java")
        override fun getInstrumentationLibraryInfo(): InstrumentationLibraryInfo = span.instrumentationLibraryInfo
        override fun hasEnded() = span.hasEnded()
        override fun getKind(): SpanKind = span.kind
        override fun getLatencyNanos(): Long = throw UnsupportedOperationException("Not supported")
        override fun getAttributes(): Attributes = span.attributes
        override fun <T> getAttribute(key: AttributeKey<T>) = span.attributes.get(key)
    }

    private data class MediaAttributes(
        val index: Int,
        val type: String,
        val field: String,
        val contentType: String? = null,
        val data: String? = null,
        val url: String? = null
    )

    companion object {
        private val TEST_TRACE_ID = "00000000000000000000000000000001"
    }
}