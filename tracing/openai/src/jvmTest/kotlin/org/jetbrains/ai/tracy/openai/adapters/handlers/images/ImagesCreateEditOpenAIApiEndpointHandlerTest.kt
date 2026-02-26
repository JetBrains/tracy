/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.openai.adapters.handlers.images

import org.jetbrains.ai.tracy.core.TracingManager
import org.jetbrains.ai.tracy.core.policy.ContentCapturePolicy
import org.jetbrains.ai.tracy.openai.adapters.BaseOpenAITracingTest
import org.jetbrains.ai.tracy.openai.clients.instrument
import org.jetbrains.ai.tracy.test.utils.MediaContentAttributeValues
import org.jetbrains.ai.tracy.test.utils.MediaSource
import org.jetbrains.ai.tracy.test.utils.toMediaContentAttributeValues
import com.openai.core.MultipartField
import com.openai.errors.InternalServerException
import com.openai.models.images.ImageEditParams
import com.openai.models.images.ImageModel
import io.opentelemetry.api.common.AttributeKey
import kotlinx.coroutines.test.runTest
import mu.KotlinLogging
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.InputStream
import java.time.Duration
import kotlin.time.Duration.Companion.minutes

private val logger = KotlinLogging.logger {}

@Tag("openai")
class ImagesCreateEditOpenAIApiEndpointHandlerTest : BaseOpenAITracingTest() {
    @Test
    fun `test tracing when editing a single image`() = runTest(timeout = 3.minutes) {
        assumeOpenAIEndpoint(patchedProviderUrl)

        val client = createOpenAIClient(
            url = patchedProviderUrl,
            timeout = Duration.ofMinutes(3)
        ).apply { instrument(this) }

        val model = ImageModel.DALL_E_2
        val prompt = "Remove cat from the image"
        val image = MediaSource.File("cat-n-dog-2-alpha.png", "image/png")

        val params = ImageEditParams.builder()
            .body(
                ImageEditParams.Body.builder()
                    .prompt(prompt)
                    .model(model)
                    .image(
                        image(image.filepath, image.contentType)
                    )
                    .responseFormat(ImageEditParams.ResponseFormat.URL)
                    .build()
            )
            .build()

        client.images().edit(params)

        validateBasicImageTracing(prompt, model)
        val trace = analyzeSpans().first()

        val expectedImage = MediaContentAttributeValues.Url(
            field = "output",
            url = null,
        )

        verifyMediaContentUploadAttributes(
            trace, expected = listOf(
                image.toMediaContentAttributeValues(field = "input"),
                expectedImage,
            )
        )
    }

    @Test
    fun `test tracing when editing an image with a mask`() = runTest(timeout = 3.minutes) {
        assumeOpenAIEndpoint(patchedProviderUrl)

        val client = createOpenAIClient(
            url = patchedProviderUrl,
            timeout = Duration.ofMinutes(3)
        ).apply { instrument(this) }

        val prompt = "Fill the mask area with beach deckchairs"
        val model = ImageModel.DALL_E_2

        val alohaImage = MediaSource.File("aloha.png", "image/png")
        val alohaMask = MediaSource.File("aloha-mask.png", "image/png")

        val editParams = ImageEditParams.builder()
            .responseFormat(ImageEditParams.ResponseFormat.URL)
            .image(
                image(alohaImage.filepath, alohaImage.contentType)
            )
            .mask(
                MultipartField.builder<InputStream>()
                    .value(readResource(alohaMask.filepath))
                    .contentType(alohaMask.contentType)
                    .filename(alohaMask.filepath)
                    .build()
            )
            .prompt(prompt)
            .model(model)
            .n(1)
            .build()

        client.images().edit(editParams)

        validateBasicImageTracing(prompt, model)
        val trace = analyzeSpans().first()

        // check mask properties attached
        assertFalse(trace.attributes[AttributeKey.stringKey("gen_ai.request.mask.content")].isNullOrEmpty())
        assertEquals(
            alohaMask.contentType,
            trace.attributes[AttributeKey.stringKey("gen_ai.request.mask.contentType")]
        )
        assertEquals(
            alohaMask.filepath,
            trace.attributes[AttributeKey.stringKey("gen_ai.request.mask.filename")]
        )

        assertEquals("1", trace.attributes[AttributeKey.stringKey("gen_ai.request.n")])

        val expectedImage = MediaContentAttributeValues.Url(
            field = "output",
            url = null,
        )

        verifyMediaContentUploadAttributes(
            trace, expected = listOf(
                alohaImage.toMediaContentAttributeValues(field = "input"),
                alohaMask.toMediaContentAttributeValues(field = "input"),
                expectedImage,
            )
        )
    }

    @Test
    fun `test tracing when editing an image with JPEG returned`() = runTest(timeout = 3.minutes) {
        val client = createOpenAIClient(
            url = patchedProviderUrl,
            timeout = Duration.ofMinutes(3)
        ).apply { instrument(this) }

        val model = ImageModel.GPT_IMAGE_1
        val prompt = "Add a 2nd cat to the image"
        val image = MediaSource.File("cat-n-dog-2.png", "image/png")

        val params = ImageEditParams.builder()
            .body(
                ImageEditParams.Body.builder()
                    .prompt(prompt)
                    .model(model)
                    .image(
                        image(image.filepath, image.contentType)
                    )
                    .outputFormat(ImageEditParams.OutputFormat.JPEG)
                    .build()
            )
            .build()

        client.images().edit(params)

        validateBasicImageTracing(prompt, model)
        val trace = analyzeSpans().first()

        val expectedImage = MediaContentAttributeValues.Data(
            field = "output",
            contentType = "image/jpeg",
            data = null,
        )

        verifyMediaContentUploadAttributes(
            trace, expected = listOf(
                image.toMediaContentAttributeValues(field = "input"),
                expectedImage,
            )
        )
    }

    @Test
    fun `test tracing when editing two images`() = runTest(timeout = 3.minutes) {
        assumeOpenAIEndpoint(patchedProviderUrl)

        val client = createOpenAIClient(
            url = patchedProviderUrl,
            timeout = Duration.ofMinutes(3)
        ).apply { instrument(this) }

        val model = ImageModel.GPT_IMAGE_1
        val prompt = "Merge two images. I want to see 2 cats and 2 dogs!"
        val contentType = "image/png"

        val image1 = MediaSource.File("cat-n-dog-1.png", contentType)
        val image2 = MediaSource.File("cat-n-dog-2.png", contentType)
        val images = listOf(image1, image2)

        val params = ImageEditParams.builder()
            .body(
                ImageEditParams.Body.builder()
                    .prompt(prompt)
                    .image(
                        images(images.map { it.filepath }, contentType)
                    )
                    .outputFormat(ImageEditParams.OutputFormat.PNG)
                    .model(model)
                    .build()
            )
            .build()

        client.images().edit(params)

        validateBasicImageTracing(prompt, model)
        val trace = analyzeSpans().first()

        val expectedImage = MediaContentAttributeValues.Data(
            field = "output",
            contentType = contentType,
            data = null,
        )

        verifyMediaContentUploadAttributes(
            trace, expected = listOf(
                image1.toMediaContentAttributeValues(field = "input"),
                image2.toMediaContentAttributeValues(field = "input"),
                expectedImage,
            )
        )
    }

    @Test
    fun `test tracing when editing two images with streaming API`() = runTest(timeout = 3.minutes) {
        assumeOpenAIEndpoint(patchedProviderUrl)

        val client = createOpenAIClient(
            url = patchedProviderUrl,
            timeout = Duration.ofMinutes(3)
        ).apply { instrument(this) }

        val model = ImageModel.GPT_IMAGE_1
        val prompt = "Merge two images!"
        val contentType = "image/png"
        val size = ImageEditParams.Size._1024X1024
        // See the description of this parameter:
        // https://platform.openai.com/docs/api-reference/images/create#images_create-partial_images
        // there will be 2 partial images and 1 final image as an output
        val partialImagesCount = 2

        val image1 = MediaSource.File("cat-n-dog-1.png", contentType)
        val image2 = MediaSource.File("cat-n-dog-2.png", contentType)
        val images = listOf(image1, image2)

        val params = ImageEditParams.builder()
            .body(
                ImageEditParams.Body.builder()
                    .prompt(prompt)
                    .image(
                        images(images.map { it.filepath }, contentType)
                    )
                    .outputFormat(ImageEditParams.OutputFormat.PNG)
                    .model(model)
                    .build()
            )
            .size(size)
            .partialImages(partialImagesCount.toLong())
            .build()

        val events = client.images().editStreaming(params).use { events ->
            events.stream().toList()
        }

        val expectedImages = buildList {
            for (e in events) {
                val b64Json = when {
                    e.isPartialImage() -> e.asPartialImage().b64Json()
                    e.isCompleted() -> e.asCompleted().b64Json()
                    else -> null
                }
                assumeTrue(b64Json != null) {
                    "One of events has no image data: $e. Two partial images and one final one expected " +
                            "(see `partial_images` parameter guarantees: https://platform.openai.com/docs/api-reference/images/create#images_create-partial_images)"
                }
                add(
                    MediaContentAttributeValues.Data(
                        field = "output",
                        contentType = contentType,
                        data = b64Json,
                    )
                )
            }
        }

        // assuming `partial_images` guarantees are held:
        //  1. 2 partial images generated
        //  2. 1 final image generated
        // see: https://platform.openai.com/docs/api-reference/images/create#images_create-partial_images
        assumeTrue(expectedImages.size == 3) {
            "Events are assumed to contain $partialImagesCount partial images and one final image, " +
                    "got ${expectedImages.joinToString { it.toString() }} " +
                    "(see `partial_images` parameter guarantees: https://platform.openai.com/docs/api-reference/images/create#images_create-partial_images)"
        }

        validateBasicImageTracing(prompt, model)
        val traces = analyzeSpans()
        assertTracesCount(1, traces)
        val trace = traces.first()

        assertEquals(
            size.asString(),
            trace.attributes[AttributeKey.stringKey("gen_ai.request.size")]
        )
        assertEquals(
            partialImagesCount.toString(),
            trace.attributes[AttributeKey.stringKey("gen_ai.request.partial_images")]
        )
        assertFalse(trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.content")].isNullOrEmpty())

        verifyMediaContentUploadAttributes(
            trace, expected = listOf(
                image1.toMediaContentAttributeValues(field = "input"),
                image2.toMediaContentAttributeValues(field = "input"),
            ) + expectedImages
        )
    }

    @ParameterizedTest
    @MethodSource("provideContentCapturePolicies")
    fun `test capture policy hides sensitive data`(policy: ContentCapturePolicy) = runTest(timeout = 3.minutes) {
        TracingManager.withCapturingPolicy(policy)

        val client = createOpenAIClient(
            url = patchedProviderUrl,
            timeout = Duration.ofMinutes(3)
        ).apply { instrument(this) }

        val model = ImageModel.GPT_IMAGE_1
        val promptMessage = "Add a 2nd cat to the image"
        val outputFormat = ImageEditParams.OutputFormat.JPEG
        val image = MediaSource.File("cat-n-dog-2.png", "image/png")

        val params = ImageEditParams.builder()
            .body(
                ImageEditParams.Body.builder()
                    .prompt(promptMessage)
                    .model(model)
                    .image(
                        image(image.filepath, image.contentType)
                    )
                    .outputFormat(outputFormat)
                    .build()
            )
            .build()

        val requestFailedWithServerError = try {
            client.images().edit(params)
            false
        } catch (err: InternalServerException) {
            logger.trace(err) { "Failed with an internal server error, status code ${err.statusCode()}" }
            true
        }
        assumeFalse(requestFailedWithServerError)

        val traces = analyzeSpans()
        assertTracesCount(1, traces)
        val trace = traces.first()

        // input attributes
        val prompt = trace.attributes[AttributeKey.stringKey("gen_ai.prompt.0.content")]
        if (!policy.captureInputs) {
            assertEquals("REDACTED", prompt, "Prompt should be redacted")
        } else {
            assertNotEquals("REDACTED", prompt, "Prompt should NOT be redacted")
        }

        // response attributes
        val completion = trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.content")]
        if (!policy.captureOutputs) {
            assertEquals("REDACTED", completion, "Completion content should be redacted")
        } else {
            assertNotEquals("REDACTED", completion, "Completion content should NOT be redacted")
        }

        // media content uploads
        val mediaContentUploads = buildList {
            if (policy.captureInputs) {
                add(image.toMediaContentAttributeValues(field = "input"))
            }
            if (policy.captureOutputs) {
                val expectedImage = MediaContentAttributeValues.Data(
                    field = "output",
                    contentType = "image/${outputFormat.value().name.lowercase()}",
                    data = null,
                )
                add(expectedImage)
            }
        }
        verifyMediaContentUploadAttributes(trace, expected = mediaContentUploads)
    }

    private fun image(filepath: String, contentType: String): MultipartField<ImageEditParams.Image> {
        val image = readResource(filepath)

        return MultipartField.builder<ImageEditParams.Image>()
            .value(ImageEditParams.Image.ofInputStream(image))
            .contentType(contentType)
            .filename(filepath)
            .build()
    }

    private fun images(filepaths: List<String>, contentType: String): MultipartField<ImageEditParams.Image> {
        val images = buildList {
            for (filepath in filepaths) {
                val image = readResource(filepath)
                add(image)
            }
        }
        return MultipartField.builder<ImageEditParams.Image>()
            .value(ImageEditParams.Image.ofInputStreams(images))
            .contentType(contentType)
            .filename(filepaths.first())
            .build()
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