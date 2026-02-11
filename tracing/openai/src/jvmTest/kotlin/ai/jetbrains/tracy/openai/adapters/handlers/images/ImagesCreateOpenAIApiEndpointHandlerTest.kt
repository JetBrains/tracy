/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package ai.jetbrains.tracy.openai.adapters.handlers.images

import ai.jetbrains.tracy.core.TracingManager
import ai.jetbrains.tracy.core.policy.ContentCapturePolicy
import ai.jetbrains.tracy.openai.adapters.BaseOpenAITracingTest
import ai.jetbrains.tracy.openai.clients.instrument
import ai.jetbrains.tracy.test.utils.MediaContentAttributeValues
import com.openai.models.images.ImageGenerateParams
import com.openai.models.images.ImageModel
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.StatusCode
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.time.Duration
import java.util.stream.Stream
import kotlin.time.Duration.Companion.minutes

@Tag("openai")
class ImagesCreateOpenAIApiEndpointHandlerTest : BaseOpenAITracingTest() {
    @ParameterizedTest
    @MethodSource("provideResponseFormats")
    fun `test generate image with different response formats`(
        responseFormat: ImageGenerateParams.ResponseFormat?
    ) = runTest(timeout = 3.minutes) {
        assumeOpenAIEndpoint(patchedProviderUrl)

        val client = instrument(
            createOpenAIClient(
                url = patchedProviderUrl,
                timeout = Duration.ofMinutes(3)
            )
        )

        val prompt = "generate an image of dog and cat sitting next to each other"
        val model = ImageModel.DALL_E_2
        val size = ImageGenerateParams.Size._256X256

        val params = ImageGenerateParams.builder()
            .prompt(prompt)
            .responseFormat(responseFormat)
            .model(model)
            .size(size)
            .n(1)
            .build()

        client.images().generate(params)

        validateBasicImageTracing(prompt, model)
        val trace = analyzeSpans().first()

        assertEquals(
            size.asString(),
            trace.attributes[AttributeKey.stringKey("gen_ai.request.size")]
        )
        assertEquals(
            responseFormat?.asString() ?: "null",
            trace.attributes[AttributeKey.stringKey("gen_ai.request.response_format")]
        )
        assertEquals("1", trace.attributes[AttributeKey.stringKey("gen_ai.request.n")])

        val expectedImage = when (responseFormat) {
            ImageGenerateParams.ResponseFormat.B64_JSON ->
                MediaContentAttributeValues.Data(
                    field = "output",
                    contentType = "image/png",
                    data = null,
                )

            ImageGenerateParams.ResponseFormat.URL, null ->
                MediaContentAttributeValues.Url(
                    field = "output",
                    url = null,
                )

            else -> error("Unexpected response format: $responseFormat")
        }

        verifyMediaContentUploadAttributes(
            trace, expected = listOf(
                expectedImage,
            )
        )
    }

    @Test
    fun `test generation of a single JPEG image gets traced`() = runTest(timeout = 3.minutes) {
        val client = instrument(
            createOpenAIClient(
                url = patchedProviderUrl,
                timeout = Duration.ofMinutes(3)
            )
        )

        val prompt = "generate an image of a cute cat"
        val model = ImageModel.GPT_IMAGE_1
        val size = ImageGenerateParams.Size._1024X1024
        val format = ImageGenerateParams.OutputFormat.JPEG

        val params = ImageGenerateParams.builder()
            .prompt(prompt)
            .model(model)
            .outputFormat(format)
            .size(size)
            .n(1)
            .build()

        client.images().generate(params)

        val traces = analyzeSpans()
        assumeTracesCount(1, traces)

        validateBasicImageTracing(prompt, model)
        val trace = traces.first()

        assertEquals(
            size.asString(),
            trace.attributes[AttributeKey.stringKey("gen_ai.request.size")]
        )
        assertEquals("1", trace.attributes[AttributeKey.stringKey("gen_ai.request.n")])
        assertEquals(format.asString(), trace.attributes[AttributeKey.stringKey("gen_ai.request.output_format")])

        val expectedImage = MediaContentAttributeValues.Data(
            field = "output",
            contentType = "image/jpeg",
            data = null,
        )

        verifyMediaContentUploadAttributes(
            trace, expected = listOf(
                expectedImage,
            )
        )
    }

    @Test
    fun `test generation of multiple images gets traced`() = runTest(timeout = 3.minutes) {
        assumeOpenAIEndpoint(patchedProviderUrl)

        val client = instrument(
            createOpenAIClient(
                url = patchedProviderUrl,
                timeout = Duration.ofMinutes(3)
            )
        )

        val prompt = "generate an image of a cute cat"
        val model = ImageModel.DALL_E_2
        val size = ImageGenerateParams.Size._256X256

        val params = ImageGenerateParams.builder()
            .prompt(prompt)
            .model(model)
            .size(size)
            .n(3)
            .build()

        client.images().generate(params)

        validateBasicImageTracing(prompt, model)
        val trace = analyzeSpans().first()

        assertEquals(
            size.asString(),
            trace.attributes[AttributeKey.stringKey("gen_ai.request.size")]
        )
        assertEquals("3", trace.attributes[AttributeKey.stringKey("gen_ai.request.n")])

        val expectedImage = MediaContentAttributeValues.Url(
            field = "output",
            url = null,
        )

        verifyMediaContentUploadAttributes(
            trace, expected = listOf(
                expectedImage, expectedImage, expectedImage
            )
        )
    }

    @Test
    fun `test invalid param 'n=0' gets traced as an error`() = runTest {
        assumeOpenAIEndpoint(patchedProviderUrl)

        val client = instrument(
            createOpenAIClient(
                url = patchedProviderUrl,
                timeout = Duration.ofMinutes(3)
            )
        )

        val prompt = "generate an image of a cute cat"
        val model = ImageModel.DALL_E_2

        val params = ImageGenerateParams.builder()
            .prompt(prompt)
            .model(model)
            .size(ImageGenerateParams.Size._256X256)
            .n(0)
            .build()

        try {
            client.images().generate(params)
        } catch (_: Exception) {
        }

        validateBasicImageTracing(prompt, model)

        val trace = analyzeSpans().first()
        assertEquals(StatusCode.ERROR, trace.status.statusCode)

        assertEquals("n", trace.attributes[AttributeKey.stringKey("gen_ai.error.param")])
        assertEquals("invalid_request_error", trace.attributes[AttributeKey.stringKey("gen_ai.error.type")])
        assertEquals("integer_below_min_value", trace.attributes[AttributeKey.stringKey("gen_ai.error.code")])
        assertEquals(true, trace.attributes[AttributeKey.stringKey("gen_ai.error.message")]?.isNotEmpty())
    }

    @Test
    fun `test image generation with streaming API`() = runTest(timeout = 3.minutes) {
        val client = instrument(
            createOpenAIClient(
                url = patchedProviderUrl,
                timeout = Duration.ofMinutes(3)
            )
        )

        val prompt = "generate an image where a knife cuts a glass watermelon"
        val model = ImageModel.GPT_IMAGE_1
        val size = ImageGenerateParams.Size._1024X1024
        val partialImagesCount = 2

        val params = ImageGenerateParams.builder()
            .prompt(prompt)
            .model(model)
            .partialImages(partialImagesCount.toLong())
            .size(size)
            .n(1)
            .build()

        client.images().generateStreaming(params).use { events ->
            events.stream().toList()
        }

        validateBasicImageTracing(prompt, model)
        val trace = analyzeSpans().first()

        assertEquals(
            size.asString(),
            trace.attributes[AttributeKey.stringKey("gen_ai.request.size")]
        )
        assertEquals(
            partialImagesCount.toString(),
            trace.attributes[AttributeKey.stringKey("gen_ai.request.partial_images")]
        )
        assertEquals("1", trace.attributes[AttributeKey.stringKey("gen_ai.request.n")])

        // expect there to be two partial images.
        // mind that it may not always be the case:
        // https://platform.openai.com/docs/api-reference/images/create#images_create-partial_images
        for (index in 0 until partialImagesCount) {
            assertEquals(
                index.toString(),
                trace.attributes[AttributeKey.stringKey("gen_ai.completion.partial_image.$index.partial_image_index")]
            )
            assertEquals(
                size.asString(),
                trace.attributes[AttributeKey.stringKey("gen_ai.completion.partial_image.$index.size")]
            )
            assertEquals(
                true,
                trace.attributes[AttributeKey.stringKey("gen_ai.completion.partial_image.$index.b64_json")]?.isNotEmpty()
            )
        }

        val expectedImage = MediaContentAttributeValues.Data(
            field = "output",
            contentType = "image/png",
            data = null,
        )

        verifyMediaContentUploadAttributes(
            trace, expected = listOf(
                expectedImage, expectedImage, expectedImage
            )
        )
    }

    @ParameterizedTest
    @MethodSource("provideContentCapturePolicies")
    fun `test capture policy hides sensitive data`(policy: ContentCapturePolicy) = runTest(timeout = 3.minutes) {
        TracingManager.withCapturingPolicy(policy)

        val client = instrument(
            createOpenAIClient(
                url = patchedProviderUrl,
                timeout = Duration.ofMinutes(3)
            )
        )

        val promptMessage = "generate an image of a cat"
        val model = ImageModel.DALL_E_2
        val params = ImageGenerateParams.builder()
            .prompt(promptMessage)
            .model(model)
            .size(ImageGenerateParams.Size._256X256)
            .n(1)
            .build()

        client.images().generate(params)

        val traces = analyzeSpans()
        assumeTracesCount(1, traces)
        val trace = traces.first()

        val prompt = trace.attributes[AttributeKey.stringKey("gen_ai.prompt.0.content")]
        if (!policy.captureInputs) {
            assertEquals("REDACTED", prompt, "Prompt should be redacted")
        } else {
            assertNotEquals("REDACTED", prompt, "Prompt should NOT be redacted")
        }

        val completion = trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.content")]
        if (!policy.captureOutputs) {
            assertEquals("REDACTED", completion, "Completion content should be redacted")
        } else {
            assertNotEquals("REDACTED", completion, "Completion content should NOT be redacted")
        }

        val uploads = buildList {
            if (policy.captureOutputs) {
                add(
                    MediaContentAttributeValues.Url(
                        field = "output",
                        url = null,
                    )
                )
            }
        }
        verifyMediaContentUploadAttributes(trace, expected = uploads)
    }

    fun provideResponseFormats(): Stream<Arguments> {
        return Stream.of(
            Arguments.of(null),
            Arguments.of(ImageGenerateParams.ResponseFormat.URL),
            Arguments.of(ImageGenerateParams.ResponseFormat.B64_JSON),
        )
    }

    private fun validateBasicImageTracing(prompt: String, model: ImageModel) {
        val traces = analyzeSpans()
        assertTracesCount(1, traces)
        val trace = traces.first()

        assertEquals(prompt, trace.attributes[AttributeKey.stringKey("gen_ai.prompt.0.content")])
        assertEquals(
            true,
            trace.attributes[AttributeKey.stringKey("gen_ai.request.model")]?.startsWith(model.asString())
        )
    }
}