/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.openai.adapters.handlers.videos

import com.openai.core.MultipartField
import com.openai.errors.NotFoundException
import com.openai.models.videos.*
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.StatusCode
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.jetbrains.ai.tracy.core.TracingManager
import org.jetbrains.ai.tracy.core.policy.ContentCapturePolicy
import org.jetbrains.ai.tracy.openai.adapters.BaseOpenAITracingTest
import org.jetbrains.ai.tracy.openai.clients.instrument
import org.jetbrains.ai.tracy.test.utils.MediaSource
import org.jetbrains.ai.tracy.test.utils.loadFile
import org.jetbrains.ai.tracy.test.utils.toMediaContentAttributeValues
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.InputStream
import java.time.Duration
import kotlin.jvm.optionals.getOrNull
import kotlin.math.pow
import kotlin.time.Duration.Companion.minutes

@Tag("openai")
class VideosOpenAIApiEndpointHandlerTest : BaseOpenAITracingTest() {
    @Test
    fun testVideos() = runTest {
        val model = VideoModel.SORA_2
        val client = createOpenAIClient().apply { instrument(this) }

        val params = VideoCreateParams.builder()
            .model(model)
            .prompt("A calico cat playing a piano on stage")
            .build()

        val video = client.videos().create(params)

        println("video:\n${video}")
        val trace = analyzeSpans().first()
        println("trace:\n${trace.attributes}")
    }

    // ============ CREATE: POST /videos ============

    @Test
    fun `test CREATE video endpoint gets traced`() = runTest(timeout = 3.minutes) {
        assumeOpenAIEndpoint(patchedProviderUrl)

        val client = createOpenAIClient(
            url = patchedProviderUrl,
            timeout = Duration.ofMinutes(3)
        ).apply { instrument(this) }

        val prompt = "A cat playing with a ball of yarn"
        val model = VideoModel.SORA_2

        val params = VideoCreateParams.builder()
            .prompt(prompt)
            .model(model)
            .build()

        val video = client.videos().create(params)

        validateBasicVideoTracing(prompt, model)
        val trace = analyzeSpans().first()

        // Verify a Video model is traced
        assertNotNull(trace.attributes[AttributeKey.stringKey("gen_ai.video.id")])
        assertEquals(video.id(), trace.attributes[AttributeKey.stringKey("gen_ai.video.id")])
        assertNotNull(trace.attributes[AttributeKey.stringKey("gen_ai.video.status")])
        assertNotNull(trace.attributes[AttributeKey.stringKey("gen_ai.video.created_at")])
    }

    @Test
    fun `test CREATE video endpoint with input reference gets traced`() = runTest(timeout = 3.minutes) {
        assumeOpenAIEndpoint(patchedProviderUrl)

        val client = createOpenAIClient(
            url = patchedProviderUrl,
            timeout = Duration.ofMinutes(3)
        ).apply { instrument(this) }

        val prompt = "Animate this image with realistic motion"
        val model = VideoModel.SORA_2
        val size = VideoSize._1280X720
        // Inpaint image must match the requested width and height,
        // i.e., dimensions of the image must match the `size` property
        val referenceFile = MediaSource.File("aloha-1280x720.png", "image/png")
        val file = loadFile(referenceFile.filepath)

        val params = VideoCreateParams.builder()
            .prompt(prompt)
            .model(model)
            .size(size)
            .inputReference(
                MultipartField.builder<InputStream>()
                    .value(file.inputStream())
                    .contentType(referenceFile.contentType)
                    .filename(referenceFile.filepath)
                    .build()
            )
            .build()

        val video = client.videos().create(params)

        validateBasicVideoTracing(prompt, model)
        val trace = analyzeSpans().first()

        assertEquals(video.id(), trace.attributes[AttributeKey.stringKey("gen_ai.response.video.id")])
        assertEquals(
            size.asString(),
            trace.attributes[AttributeKey.stringKey("gen_ai.request.size")]
        )
        // verify input reference is traced
        assertNotNull(trace.attributes[AttributeKey.stringKey("gen_ai.request.input_reference.content")])
        assertEquals(
            referenceFile.contentType,
            trace.attributes[AttributeKey.stringKey("gen_ai.request.input_reference.contentType")]
        )
        assertEquals(
            referenceFile.filepath,
            trace.attributes[AttributeKey.stringKey("gen_ai.request.input_reference.filename")]
        )

        verifyMediaContentUploadAttributes(
            trace, expected = listOf(
                referenceFile.toMediaContentAttributeValues(field = "input")
            )
        )
    }

    @Test
    fun `test CREATE video endpoint with duration and size parameters gets traced`() = runTest(timeout = 3.minutes) {
        assumeOpenAIEndpoint(patchedProviderUrl)

        val client = createOpenAIClient(
            url = patchedProviderUrl,
            timeout = Duration.ofMinutes(3)
        ).apply { instrument(this) }

        val prompt = "A serene sunset over the ocean"
        val model = VideoModel.SORA_2_PRO
        val seconds = VideoSeconds._8
        val size = VideoSize._1280X720

        val params = VideoCreateParams.builder()
            .prompt(prompt)
            .model(model)
            .seconds(seconds)
            .size(size)
            .build()

        val video = client.videos().create(params)

        validateBasicVideoTracing(prompt, model)
        val trace = analyzeSpans().first()

        assertEquals(video.id(), trace.attributes[AttributeKey.stringKey("gen_ai.response.video.id")])
        assertEquals(seconds.asString(), trace.attributes[AttributeKey.stringKey("gen_ai.request.seconds")])
        assertEquals(size.asString(), trace.attributes[AttributeKey.stringKey("gen_ai.request.size")])
    }

    @Test
    fun `test CREATE video endpoint failure with invalid parameters gets traced`() = runTest {
        assumeOpenAIEndpoint(patchedProviderUrl)

        val client = createOpenAIClient(
            url = patchedProviderUrl,
            timeout = Duration.ofMinutes(3)
        ).apply { instrument(this) }

        val prompt = "" // Invalid empty prompt
        val model = VideoModel.SORA_2

        val params = VideoCreateParams.builder()
            .prompt(prompt)
            .model(model)
            .build()

        try {
            client.videos().create(params)
        } catch (_: Exception) {
            // Expected to fail
        }

        val traces = analyzeSpans()
        assertTracesCount(1, traces)
        val trace = traces.first()

        assertEquals(StatusCode.ERROR, trace.status.statusCode)
    }

    @ParameterizedTest
    @MethodSource("provideContentCapturePolicies")
    fun `test capture policy hides sensitive data for CREATE video endpoint`(policy: ContentCapturePolicy) =
        runTest(timeout = 3.minutes) {
            TracingManager.withCapturingPolicy(policy)

            val client = createOpenAIClient(
                url = patchedProviderUrl,
                timeout = Duration.ofMinutes(3)
            ).apply { instrument(this) }

            val promptMessage = "A beautiful landscape with mountains"
            val model = VideoModel.SORA_2

            val params = VideoCreateParams.builder()
                .prompt(promptMessage)
                .model(model)
                .build()

            client.videos().create(params)

            val traces = analyzeSpans()
            assumeTracesCount(1, traces)
            val trace = traces.first()

            // Check prompt redaction
            val prompt = trace.attributes[AttributeKey.stringKey("gen_ai.prompt.0.content")]
            if (!policy.captureInputs) {
                assertEquals("REDACTED", prompt, "Prompt should be redacted")
            } else {
                assertNotEquals("REDACTED", prompt, "Prompt should NOT be redacted")
            }

            // Check video prompt in response (output)
            val videoPrompt = trace.attributes[AttributeKey.stringKey("gen_ai.video.prompt")]
            if (videoPrompt != null) {
                if (!policy.captureOutputs) {
                    assertEquals("REDACTED", videoPrompt, "Video prompt should be redacted")
                } else {
                    assertNotEquals("REDACTED", videoPrompt, "Video prompt should NOT be redacted")
                }
            }
        }

    // ============ GET_VIDEO: GET /videos/{video_id} ============

    @Test
    fun `test video status from GET_VIDEO endpoint gets traced`() = runTest(timeout = 3.minutes) {
        assumeOpenAIEndpoint(patchedProviderUrl)

        val client = createOpenAIClient(
            url = patchedProviderUrl,
            timeout = Duration.ofMinutes(3)
        ).apply { instrument(this) }

        // first, create a video
        val createParams = VideoCreateParams.builder()
            .prompt("A dog running")
            .model(VideoModel.SORA_2)
            .build()
        val createdVideoId = client.videos().create(createParams).id()

        // Now retrieve it
        val retrievedVideo = client.videos().retrieve(createdVideoId)

        val traces = analyzeSpans()
        assertTracesCount(2, traces)
        val trace = traces.last()

        // verify requested_id is traced
        assertEquals(createdVideoId, trace.attributes[AttributeKey.stringKey("gen_ai.request.video.requested_id")])

        // verify a Video model is traced
        assertEquals(createdVideoId, trace.attributes[AttributeKey.stringKey("gen_ai.response.video.id")])
        assertEquals(
            retrievedVideo.status().asString(),
            trace.attributes[AttributeKey.stringKey("gen_ai.response.video.status")],
        )
        assertEquals(
            retrievedVideo.model().asString(),
            trace.attributes[AttributeKey.stringKey("gen_ai.response.video.model")],
        )
    }

    @Test
    fun `test progress and timestamps from GET_VIDEO endpoint get traced`() = runTest(timeout = 3.minutes) {
        assumeOpenAIEndpoint(patchedProviderUrl)

        val client = createOpenAIClient(
            url = patchedProviderUrl,
            timeout = Duration.ofMinutes(3)
        ).apply { instrument(this) }

        val createParams = VideoCreateParams.builder()
            .prompt("Test video")
            .model(VideoModel.SORA_2)
            .build()
        val video = client.videos().create(createParams)

        client.videos().retrieve(video.id())

        val traces = analyzeSpans()
        assertTracesCount(2, traces)
        val trace = traces.last()

        // Progress might be present
        assertNotNull(trace.attributes[AttributeKey.stringKey("gen_ai.response.video.progress")])
        // created_at should always be present
        assertNotNull(trace.attributes[AttributeKey.stringKey("gen_ai.response.video.created_at")])
    }

    // ============ GET /videos (LIST) ============

    @Test
    fun `test videos metadata from LIST endpoint gets traced`() = runTest(timeout = 3.minutes) {
        assumeOpenAIEndpoint(patchedProviderUrl)

        val client = createOpenAIClient(
            url = patchedProviderUrl,
            timeout = Duration.ofMinutes(3)
        ).apply { instrument(this) }

        val listParams = VideoListParams.builder().build()
        val videoList = client.videos().list(listParams)

        val traces = analyzeSpans()
        assertTracesCount(1, traces)
        val trace = traces.first()

        println("trace.attributes:")
        println(trace.attributes)

        // Verify list response attributes
        val videosCount = trace.attributes[AttributeKey.longKey("gen_ai.response.videos_count")]

        assertEquals(videoList.data().size.toLong(), videosCount)
        assertNotNull(trace.attributes[AttributeKey.booleanKey("gen_ai.response.has_more")])
        assertEquals("list", trace.attributes[AttributeKey.stringKey("gen_ai.operation.name")])

        // Verify individual videos are traced
        if (videosCount != null && videosCount > 0) {
            // if at least one video is present, verify its traced properties
            assertNotNull(trace.attributes[AttributeKey.stringKey("gen_ai.response.videos.0.id")])
            assertNotNull(trace.attributes[AttributeKey.stringKey("gen_ai.response.videos.0.status")])
        }
    }

    @Test
    fun `test query parameters from LIST endpoint get traced`() = runTest(timeout = 3.minutes) {
        assumeOpenAIEndpoint(patchedProviderUrl)

        val client = createOpenAIClient(
            url = patchedProviderUrl,
            timeout = Duration.ofMinutes(3)
        ).apply { instrument(this) }

        val limit = 5L
        val order = "desc"

        val listParams = VideoListParams.builder()
            .limit(limit)
            .order(VideoListParams.Order.DESC)
            .build()

        client.videos().list(listParams)

        val trace = analyzeSpans().first()

        // verify query parameters are traced
        assertEquals(limit.toString(), trace.attributes[AttributeKey.stringKey("gen_ai.request.limit")])
        assertEquals(order, trace.attributes[AttributeKey.stringKey("gen_ai.request.order")])
    }

    @Test
    fun `list with after cursor from LIST endpoint gets traced`() = runTest(timeout = 3.minutes) {
        assumeOpenAIEndpoint(patchedProviderUrl)

        val client = createOpenAIClient(
            url = patchedProviderUrl,
            timeout = Duration.ofMinutes(3)
        ).apply { instrument(this) }

        // NOTE: This ID doesn't exist on the backend, so the request fails
        val after = "video_abc123"

        val listParams = VideoListParams.builder()
            .after(after)
            .build()

        try {
            client.videos().list(listParams)
        } catch (_: NotFoundException) {
            // no-op, expected to fail
        }

        val traces = analyzeSpans()
        assertTracesCount(1, traces)
        val trace = traces.first()
        assertEquals(after, trace.attributes[AttributeKey.stringKey("gen_ai.request.after")])
    }

    // ============ DELETE: DELETE /videos/{video_id} ============

    @Test
    fun `test delete metadata from DELETE endpoint gets traced`() = runTest(timeout = 5.minutes) {
        assumeOpenAIEndpoint(patchedProviderUrl)

        val client = createOpenAIClient(
            url = patchedProviderUrl,
            timeout = Duration.ofMinutes(5)
        ).apply { instrument(this) }

        // Create a video first with minimal params
        val createParams = VideoCreateParams.builder()
            .prompt("Temporary video")
            .model(VideoModel.SORA_2)
            .seconds(VideoSeconds._4)
            .size(VideoSize._1280X720)
            .build()
        val video = client.videos().create(createParams)

        // Wait for completion before deleting
        val completedVideo = awaitVideoCompletion(client, video.id())
        // Delete it
        val deleteResponse = client.videos().delete(completedVideo.id())

        val traces = analyzeSpans()
        assertTracesCount(2, traces)
        val trace = traces.last()

        // Verify requested_id is traced
        assertEquals(completedVideo.id(), trace.attributes[AttributeKey.stringKey("gen_ai.request.video.requested_id")])

        // Verify deletion response
        assertEquals(deleteResponse.id(), trace.attributes[AttributeKey.stringKey("gen_ai.response.id")])
        assertEquals(true, trace.attributes[AttributeKey.booleanKey("gen_ai.response.deleted")])
        assertEquals("video.deleted", trace.attributes[AttributeKey.stringKey("gen_ai.operation.name")])
    }

    // ============ VIDEO_CONTENT: GET /videos/{video_id}/content ============

    @Test
    fun `test downloaded video content from VIDEO_CONTENT endpoint gets traced`() = runTest(timeout = 3.minutes) {
        assumeOpenAIEndpoint(patchedProviderUrl)

        val client = createOpenAIClient(
            url = patchedProviderUrl,
            timeout = Duration.ofMinutes(3)
        ).apply { instrument(this) }

        // Create video with minimal params
        val createParams = VideoCreateParams.builder()
            .prompt("Quick video")
            .model(VideoModel.SORA_2)
            .seconds(VideoSeconds._4)
            .size(VideoSize._1280X720)
            .build()
        val video = client.videos().create(createParams)

        // Wait for completion before downloading
        val completedVideo = awaitVideoCompletion(client, video.id())

        // Download content
        // TODO: trace media span upload attributes (similar to images)
        val content = client.videos().downloadContent(completedVideo.id())

        val traces = analyzeSpans()
        assertTracesCount(2, traces)
        val trace = traces.last()

        // Verify requested_id
        assertEquals(completedVideo.id(), trace.attributes[AttributeKey.stringKey("gen_ai.request.video.requested_id")])

        // Verify binary stream metadata
        assertEquals("video/mp4", trace.attributes[AttributeKey.stringKey("gen_ai.response.content_type")])
        assertEquals(true, trace.attributes[AttributeKey.booleanKey("gen_ai.response.is_binary_stream")])
    }

    @Test
    fun `test GET videos content - with variant parameter`() = runTest(timeout = 3.minutes) {
        assumeOpenAIEndpoint(patchedProviderUrl)

        val client = createOpenAIClient(
            url = patchedProviderUrl,
            timeout = Duration.ofMinutes(3)
        ).apply { instrument(this) }

        val createParams = VideoCreateParams.builder()
            .prompt("Test video")
            .model(VideoModel.SORA_2)
            .seconds(VideoSeconds._4)
            .size(VideoSize._1280X720)
            .build()
        val video = client.videos().create(createParams)

        // Wait for completion before downloading
        val completedVideo = awaitVideoCompletion(client, video.id())

        // TODO: trace media upload attributes (similar to images)
        val variant = VideoDownloadContentParams.Variant.VIDEO
        val downloadedContent = client.videos().downloadContent(
            VideoDownloadContentParams.builder()
                .variant(variant)
                .build()
        )

        val traces = analyzeSpans()
        assertTracesCount(2, traces)
        val trace = traces.first()

        assertEquals(completedVideo.id(), trace.attributes[AttributeKey.stringKey("gen_ai.request.video.requested_id")])
        assertEquals(variant.asString(), trace.attributes[AttributeKey.stringKey("gen_ai.request.variant")])
    }

    // ============ POST /videos/{video_id}/remix (REMIX) ============

    @Test
    fun `test POST videos remix - remix existing video`() = runTest(timeout = 3.minutes) {
        assumeOpenAIEndpoint(patchedProviderUrl)

        val client = createOpenAIClient(
            url = patchedProviderUrl,
            timeout = Duration.ofMinutes(3)
        ).apply { instrument(this) }

        // Create an original video with minimal params
        val createParams = VideoCreateParams.builder()
            .prompt("Create an original video")
            .model(VideoModel.SORA_2)
            .seconds(VideoSeconds._4)
            .size(VideoSize._1280X720)
            .build()
        val originalVideo = client.videos().create(createParams)

        // Wait for completion before remixing
        val completedVideo = awaitVideoCompletion(client, originalVideo.id())

        resetExporter()

        // Remix it
        val remixPrompt = "Make the colors more vibrant"
        val remixParams = VideoRemixParams.builder()
            .prompt(remixPrompt)
            .build()

        val remixedVideo = client.videos().remix(completedVideo.id(), remixParams)

        val traces = analyzeSpans()
        assertTracesCount(1, traces)
        val trace = traces.first()

        // Verify source video ID
        assertEquals(completedVideo.id(), trace.attributes[AttributeKey.stringKey("gen_ai.video.source_id")])

        // Verify remix prompt
        assertEquals(remixPrompt, trace.attributes[AttributeKey.stringKey("gen_ai.prompt.0.content")])

        // Verify a remixed Video model
        assertEquals(remixedVideo.id(), trace.attributes[AttributeKey.stringKey("gen_ai.video.id")])
        assertNotNull(trace.attributes[AttributeKey.stringKey("gen_ai.video.status")])

        // Verify remixed_from_video_id if present
        assertEquals(
            completedVideo.id(),
            trace.attributes[AttributeKey.stringKey("gen_ai.video.remixed_from_video_id")]
        )
    }

    // ============ VIDEO MODEL TRACING ============

    @Test
    fun `test Video model - all fields are traced correctly`() = runTest(timeout = 3.minutes) {
        assumeOpenAIEndpoint(patchedProviderUrl)

        val client = createOpenAIClient(
            url = patchedProviderUrl,
            timeout = Duration.ofMinutes(3)
        ).apply { instrument(this) }

        val prompt = "Comprehensive test video"
        val model = VideoModel.SORA_2_PRO
        val seconds = VideoSeconds._4
        val size = VideoSize._1280X720

        val params = VideoCreateParams.builder()
            .prompt(prompt)
            .model(model)
            .seconds(seconds)
            .size(size)
            .build()

        val video = client.videos().create(params)

        val trace = analyzeSpans().first()

        // Verify all Video model fields are traced
        assertEquals(video.id(), trace.attributes[AttributeKey.stringKey("gen_ai.video.id")])
        assertEquals(prompt, trace.attributes[AttributeKey.stringKey("gen_ai.video.prompt")])
        assertTrue(trace.attributes[AttributeKey.stringKey("gen_ai.video.model")]?.startsWith(model.asString()) == true)
        assertNotNull(trace.attributes[AttributeKey.stringKey("gen_ai.video.status")])
        assertEquals("video", trace.attributes[AttributeKey.stringKey("gen_ai.video.object")])
        assertNotNull(trace.attributes[AttributeKey.stringKey("gen_ai.video.created_at")])

        // These might be present depending on status
        val tracedSeconds = trace.attributes[AttributeKey.stringKey("gen_ai.video.seconds")]
        val tracedSize = trace.attributes[AttributeKey.stringKey("gen_ai.video.size")]
        val expiresAt = trace.attributes[AttributeKey.stringKey("gen_ai.video.expires_at")]

        if (tracedSeconds != null) {
            assertEquals(seconds.asString(), tracedSeconds)
        }
        if (tracedSize != null) {
            assertEquals(size.asString(), tracedSize)
        }
        if (expiresAt != null) {
            assertNotNull(expiresAt)
        }
    }

    @Test
    fun `test VideoCreateError - error fields are traced`() = runTest {
        assumeOpenAIEndpoint(patchedProviderUrl)

        val client = createOpenAIClient(
            url = patchedProviderUrl,
            timeout = Duration.ofMinutes(3)
        ).apply { instrument(this) }

        // Trigger an error
        val params = VideoCreateParams.builder()
            .prompt("") // Invalid
            .model(VideoModel.SORA_2)
            .build()

        try {
            client.videos().create(params)
        } catch (_: Exception) {
            // Expected
        }

        val trace = analyzeSpans().first()

        // Some error information should be traced
        assertEquals(StatusCode.ERROR, trace.status.statusCode)
    }

    // ============ HELPER METHODS ============

    private fun validateBasicVideoTracing(prompt: String, model: VideoModel) {
        val traces = analyzeSpans()
        assertTracesCount(1, traces)
        val trace = traces.first()

        assertEquals(prompt, trace.attributes[AttributeKey.stringKey("gen_ai.prompt.0.content")])
        assertTrue(
            trace.attributes[AttributeKey.stringKey("gen_ai.request.model")]?.startsWith(model.asString()) == true,
            "Model should match"
        )
    }

    /**
     * Waits for video generation to complete using exponential backoff polling.
     * Does NOT trace polling requests to avoid polluting test traces.
     *
     * @param client The OpenAI client to use for polling
     * @param videoId The video ID to poll
     * @param maxAttempts Maximum polling attempts (default: 20)
     * @param initialDelayMs Initial delay between polls in ms (default: 2000)
     * @param maxDelayMs Maximum delay between polls in ms (default: 30000)
     * @param backoffFactor Exponential backoff multiplier (default: 1.5)
     * @return The completed Video object
     * @throws IllegalStateException if video generation failed
     * @throws IllegalStateException if max attempts exceeded (timeout)
     */
    private suspend fun awaitVideoCompletion(
        client: com.openai.client.OpenAIClient,
        videoId: String,
        maxAttempts: Int = 20,
        initialDelayMs: Long = 2000,
        maxDelayMs: Long = 30_000,
        backoffFactor: Double = 1.5
    ): Video {
        var currentDelay = initialDelayMs
        var attempt = 0

        // Temporarily disable tracing to avoid polluting test traces
        val originalTracingState = TracingManager.isTracingEnabled
        TracingManager.isTracingEnabled = false

        try {
            while (attempt < maxAttempts) {
                println("Attempt $attempt: Polling for video completion (wait time: $currentDelay ms)")
                delay(currentDelay)

                val video = client.videos().retrieve(videoId)
                println("Video status: ${video.status().asString()}")

                when (val status = video.status().asString()) {
                    "completed" -> return video
                    "failed" -> {
                        val errorMsg = video.error().getOrNull()?.message() ?: "Unknown error"
                        throw IllegalStateException("Video generation failed: $errorMsg")
                    }
                    "queued", "in_progress" -> {
                        // continue polling with exponential backoff
                        currentDelay = (currentDelay * backoffFactor).toLong().coerceAtMost(maxDelayMs)
                        attempt++
                    }
                    else -> throw IllegalStateException("Unknown video status: $status")
                }
            }

            val totalWaitTime = (0 until maxAttempts).fold(0L) { acc, i ->
                val ithBackoffFactor = backoffFactor.pow(i.toDouble())
                acc + (initialDelayMs * ithBackoffFactor).toLong().coerceAtMost(maxDelayMs)
            }

            throw IllegalStateException(
                "Video generation did not complete within $maxAttempts attempts (total wait time: ~${totalWaitTime} ms)"
            )
        } finally {
            // Restore the original tracing state
            TracingManager.isTracingEnabled = originalTracingState
        }
    }
}
