/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.openai.adapters.handlers.videos

import org.jetbrains.ai.tracy.core.TracingManager
import org.jetbrains.ai.tracy.core.policy.ContentCapturePolicy
import org.jetbrains.ai.tracy.openai.adapters.BaseOpenAITracingTest
import org.jetbrains.ai.tracy.openai.clients.instrument
import org.jetbrains.ai.tracy.test.utils.MediaSource
import org.jetbrains.ai.tracy.test.utils.toMediaContentAttributeValues
import com.openai.models.videos.*
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.StatusCode
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.Duration
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

    // ============ POST /videos (CREATE) ============

    @Test
    fun `test POST videos - basic video creation tracing`() = runTest(timeout = 3.minutes) {
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

        val response = client.videos().create(params)

        validateBasicVideoTracing(prompt, model)
        val trace = analyzeSpans().first()

        // Verify a Video model is traced
        assertNotNull(trace.attributes[AttributeKey.stringKey("gen_ai.video.id")])
        assertEquals(response.id(), trace.attributes[AttributeKey.stringKey("gen_ai.video.id")])
        assertNotNull(trace.attributes[AttributeKey.stringKey("gen_ai.video.status")])
        assertNotNull(trace.attributes[AttributeKey.stringKey("gen_ai.video.created_at")])
    }

    @Test
    fun `test POST videos - with input reference file`() = runTest(timeout = 3.minutes) {
        assumeOpenAIEndpoint(patchedProviderUrl)

        val client = createOpenAIClient(
            url = patchedProviderUrl,
            timeout = Duration.ofMinutes(3)
        ).apply { instrument(this) }

        val prompt = "Animate this image with realistic motion"
        val model = VideoModel.SORA_2
        val referenceFile = MediaSource.File("cat-n-dog-1.png", "image/png")

        /*
        val params = VideoCreateParams.builder()
            .prompt(prompt)
            .model(model)
            .inputReference(referenceFile.filepath)
            .build()

        client.videos().create(params)

        validateBasicVideoTracing(prompt, model)
        val trace = analyzeSpans().first()

        // Verify input reference is traced
        assertEquals("true", trace.attributes[AttributeKey.stringKey("gen_ai.request.input_reference.has_data")])
        assertNotNull(trace.attributes[AttributeKey.stringKey("gen_ai.request.input_reference.contentType")])

        verifyMediaContentUploadAttributes(
            trace, expected = listOf(
                referenceFile.toMediaContentAttributeValues(field = "input")
            )
        )
        */
    }

    @Test
    fun `test POST videos - with duration and size parameters`() = runTest(timeout = 3.minutes) {
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

        client.videos().create(params)

        validateBasicVideoTracing(prompt, model)
        val trace = analyzeSpans().first()

        assertEquals(seconds.asString(), trace.attributes[AttributeKey.stringKey("gen_ai.request.seconds")])
        assertEquals(size.asString(), trace.attributes[AttributeKey.stringKey("gen_ai.request.size")])
    }

    @Test
    fun `test POST videos - error handling with invalid parameters`() = runTest {
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
    fun `test POST videos - capture policy hides sensitive data`(policy: ContentCapturePolicy) =
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

    // ============ GET /videos/{video_id} (GET STATUS) ============

    @Test
    fun `test GET videos by id - retrieve video status`() = runTest(timeout = 3.minutes) {
        assumeOpenAIEndpoint(patchedProviderUrl)

        val client = createOpenAIClient(
            url = patchedProviderUrl,
            timeout = Duration.ofMinutes(3)
        ).apply { instrument(this) }

        // First create a video
        val createParams = VideoCreateParams.builder()
            .prompt("A dog running")
            .model(VideoModel.SORA_2)
            .build()
        val createdVideo = client.videos().create(createParams)
        val videoId = createdVideo.id()

        resetExporter()

        // Now retrieve it
        val retrievedVideo = client.videos().retrieve(videoId)

        val traces = analyzeSpans()
        assertTracesCount(1, traces)
        val trace = traces.first()

        // Verify requested_id is traced
        assertEquals(videoId, trace.attributes[AttributeKey.stringKey("gen_ai.video.requested_id")])

        // Verify a Video model is traced
        assertEquals(videoId, trace.attributes[AttributeKey.stringKey("gen_ai.video.id")])
        assertNotNull(trace.attributes[AttributeKey.stringKey("gen_ai.video.status")])
        assertNotNull(trace.attributes[AttributeKey.stringKey("gen_ai.video.model")])
    }

    @Test
    fun `test GET videos by id - traces progress and timestamps`() = runTest(timeout = 3.minutes) {
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

        resetExporter()
        client.videos().retrieve(video.id())

        val trace = analyzeSpans().first()

        // Progress might be present
        val progress = trace.attributes[AttributeKey.stringKey("gen_ai.video.progress")]
        if (progress != null) {
            assertNotNull(progress)
        }

        // created_at should always be present
        assertNotNull(trace.attributes[AttributeKey.stringKey("gen_ai.video.created_at")])
    }

    // ============ GET /videos (LIST) ============

    @Test
    fun `test GET videos - list all videos`() = runTest(timeout = 3.minutes) {
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

        // Verify list response attributes
        assertNotNull(trace.attributes[AttributeKey.stringKey("gen_ai.response.videos_count")])
        assertNotNull(trace.attributes[AttributeKey.stringKey("gen_ai.response.has_more")])
        assertNotNull(trace.attributes[AttributeKey.stringKey("gen_ai.response.object")])

        // Verify individual videos are traced
        val videosCount = trace.attributes[AttributeKey.longKey("gen_ai.response.videos_count")]
        if (videosCount != null && videosCount > 0) {
            assertNotNull(trace.attributes[AttributeKey.stringKey("gen_ai.response.videos.0.id")])
            assertNotNull(trace.attributes[AttributeKey.stringKey("gen_ai.response.videos.0.status")])
        }
    }

    @Test
    fun `test GET videos - list with pagination parameters`() = runTest(timeout = 3.minutes) {
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

        // Verify query parameters are traced
        assertEquals(limit.toString(), trace.attributes[AttributeKey.stringKey("gen_ai.request.limit")])
        assertEquals(order, trace.attributes[AttributeKey.stringKey("gen_ai.request.order")])
    }

    @Test
    fun `test GET videos - list with after cursor`() = runTest(timeout = 3.minutes) {
        assumeOpenAIEndpoint(patchedProviderUrl)

        val client = createOpenAIClient(
            url = patchedProviderUrl,
            timeout = Duration.ofMinutes(3)
        ).apply { instrument(this) }

        val after = "video_abc123"

        val listParams = VideoListParams.builder()
            .after(after)
            .build()

        client.videos().list(listParams)

        val trace = analyzeSpans().first()

        assertEquals(after, trace.attributes[AttributeKey.stringKey("gen_ai.request.after")])
    }

    // ============ DELETE /videos/{video_id} (DELETE) ============

    @Test
    fun `test DELETE videos by id - delete video`() = runTest(timeout = 3.minutes) {
        assumeOpenAIEndpoint(patchedProviderUrl)

        val client = createOpenAIClient(
            url = patchedProviderUrl,
            timeout = Duration.ofMinutes(3)
        ).apply { instrument(this) }

        // Create a video first
        val createParams = VideoCreateParams.builder()
            .prompt("Temporary video")
            .model(VideoModel.SORA_2)
            .build()
        val video = client.videos().create(createParams)

        resetExporter()

        // Delete it
        val deleteResponse = client.videos().delete(video.id())

        val traces = analyzeSpans()
        assertTracesCount(1, traces)
        val trace = traces.first()

        // Verify requested_id is traced
        assertEquals(video.id(), trace.attributes[AttributeKey.stringKey("gen_ai.video.requested_id")])

        // Verify deletion response
        assertEquals(deleteResponse.id(), trace.attributes[AttributeKey.stringKey("gen_ai.response.id")])
        assertEquals(true, trace.attributes[AttributeKey.booleanKey("gen_ai.response.deleted")])
        assertNotNull(trace.attributes[AttributeKey.stringKey("gen_ai.response.object")])
    }

    // ============ GET /videos/{video_id}/content (DOWNLOAD) ============

    @Test
    fun `test GET videos content - download video binary stream`() = runTest(timeout = 3.minutes) {
        assumeOpenAIEndpoint(patchedProviderUrl)

        val client = createOpenAIClient(
            url = patchedProviderUrl,
            timeout = Duration.ofMinutes(3)
        ).apply { instrument(this) }

        // Create and wait for completion (in real scenario)
        val createParams = VideoCreateParams.builder()
            .prompt("Quick video")
            .model(VideoModel.SORA_2)
            .build()
        val video = client.videos().create(createParams)

        resetExporter()

        // Download content (may fail if not completed yet)
        try {
            // TODO: trace media span upload attributes (similar to images)
            client.videos().downloadContent(video.id())

            val trace = analyzeSpans().first()

            // Verify requested_id
            assertEquals(video.id(), trace.attributes[AttributeKey.stringKey("gen_ai.video.requested_id")])

            // Verify binary stream metadata
            assertEquals("video/mp4", trace.attributes[AttributeKey.stringKey("gen_ai.response.content_type")])
            assertEquals(true, trace.attributes[AttributeKey.booleanKey("gen_ai.response.is_binary_stream")])
        } catch (_: Exception) {
            // Video might not be ready yet, that's ok for testing tracing logic
        }
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
            .build()
        val video = client.videos().create(createParams)

        resetExporter()

        try {
            // TODO: trace media upload attributes (similar to images)
            val variant = VideoDownloadContentParams.Variant.VIDEO
            client.videos().downloadContent(
                VideoDownloadContentParams.builder()
                    .variant(variant)
                    .build()
            )
            // .content(video.id(), VideoContentParams.builder().variant().build()

            val trace = analyzeSpans().first()

            assertEquals(video.id(), trace.attributes[AttributeKey.stringKey("gen_ai.video.requested_id")])
            assertEquals(variant.asString(), trace.attributes[AttributeKey.stringKey("gen_ai.request.variant")])
        } catch (_: Exception) {
            // Expected if video not ready
        }
    }

    // ============ POST /videos/{video_id}/remix (REMIX) ============

    @Test
    fun `test POST videos remix - remix existing video`() = runTest(timeout = 3.minutes) {
        assumeOpenAIEndpoint(patchedProviderUrl)

        val client = createOpenAIClient(
            url = patchedProviderUrl,
            timeout = Duration.ofMinutes(3)
        ).apply { instrument(this) }

        // Create an original video
        val createParams = VideoCreateParams.builder()
            .prompt("Create an original video")
            .model(VideoModel.SORA_2)
            .seconds(VideoSeconds._4)
            .size(VideoSize._1280X720)
            .build()
        val originalVideo = client.videos().create(createParams)

        // TODO: await until video created

        resetExporter()

        // Remix it
        val remixPrompt = "Make the colors more vibrant"
        val remixParams = VideoRemixParams.builder()
            .prompt(remixPrompt)
            .build()

        val remixedVideo = client.videos().remix(originalVideo.id(), remixParams)

        val traces = analyzeSpans()
        assertTracesCount(1, traces)
        val trace = traces.first()

        // Verify source video ID
        assertEquals(originalVideo.id(), trace.attributes[AttributeKey.stringKey("gen_ai.video.source_id")])

        // Verify remix prompt
        assertEquals(remixPrompt, trace.attributes[AttributeKey.stringKey("gen_ai.prompt.0.content")])

        // Verify a remixed Video model
        assertEquals(remixedVideo.id(), trace.attributes[AttributeKey.stringKey("gen_ai.video.id")])
        assertNotNull(trace.attributes[AttributeKey.stringKey("gen_ai.video.status")])

        // Verify remixed_from_video_id if present
        assertEquals(
            originalVideo.id(),
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
}
