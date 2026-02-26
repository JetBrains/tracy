/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.gemini

import org.jetbrains.ai.tracy.gemini.clients.instrument
import org.jetbrains.ai.tracy.test.utils.MediaContentAttributeValues
import org.jetbrains.ai.tracy.test.utils.MediaSource
import org.jetbrains.ai.tracy.test.utils.toMediaContentAttributeValues
import com.google.genai.types.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.time.Duration
import kotlin.time.Duration.Companion.minutes
import com.google.genai.types.GenerateContentConfig as GeminiGenerateContentConfig


// TODO: fix
// require the provider to be LiteLLM
@EnabledIfEnvironmentVariable(
    named = "LLM_PROVIDER_URL",
    matches = "https://litellm.labs.jb.gg",
    disabledReason = "LLM_PROVIDER_URL environment variable is not https://litellm.labs.jb.gg",
)
@Tag("gemini")
class GeminiMediaContentTracingTest : BaseGeminiTracingTest() {
    @Test
    fun `test generated image get traced`() = runTest(timeout = 3.minutes) {
        val client = createGeminiClient(
            timeout = Duration.ofMinutes(3)
        ).apply { instrument(this) }

        val model = "gemini-2.5-flash-image"
        val params = GeminiGenerateContentConfig.builder()
            .responseModalities("TEXT", "IMAGE")
            .build()

        client.models.generateContent(
            model,
            "Generate a single image of a restaurant",
            params,
        )

        validateBasicTracing(model)
        val trace = analyzeSpans().first()

        val expectedImage = MediaContentAttributeValues.Data(
            field = "output",
            contentType = "image/png",
            data = null,
        )
        verifyMediaContentUploadAttributes(
            trace, expected = listOf(
                expectedImage,
            )
        )
    }

    @Test
    fun `test generated image and attached reference get traced`() = runTest(timeout = 3.minutes) {
        val client = createGeminiClient(
            timeout = Duration.ofMinutes(3)
        ).apply { instrument(this) }

        val model = "gemini-2.5-flash-image"
        val params = GeminiGenerateContentConfig.builder()
            .responseModalities("TEXT", "IMAGE")
            .build()

        val image = MediaSource.File("image.jpg", "image/jpeg")

        val prompt = Content.fromParts(
            Part.fromText("Replace dogs with cats in this image"),
            Part.fromBytes(readResource(image.filepath).readAllBytes(), "image/jpeg")
        )

        client.models.generateContent(
            model,
            prompt,
            params,
        )

        val traces = analyzeSpans()
        assertTracesCount(1, traces)

        validateBasicTracing(model)
        val trace = traces.first()

        val expectedImage = MediaContentAttributeValues.Data(
            field = "output",
            contentType = "image/png",
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
    fun `test image generated in chat gets traced`() = runTest(timeout = 3.minutes) {
        val client = createGeminiClient(
            timeout = Duration.ofMinutes(3)
        ).apply { instrument(this) }

        val model = "gemini-2.5-flash-image"
        val params = GeminiGenerateContentConfig.builder()
            .responseModalities("TEXT", "IMAGE")
            .build()

        val chat = client.chats.create(model, params)
        chat.sendMessage("Create a vibrant infographic that explains photosynthesis")

        validateBasicTracing(model)
        val trace = analyzeSpans().first()

        val expectedImage = MediaContentAttributeValues.Data(
            field = "output",
            contentType = "image/png",
            data = null,
        )
        verifyMediaContentUploadAttributes(
            trace, expected = listOf(
                expectedImage,
            )
        )
    }

    @Test
    fun `test images generated in multi-turn chat generation get traced`() = runTest(timeout = 3.minutes) {
        val client = createGeminiClient(
            timeout = Duration.ofMinutes(3)
        ).apply { instrument(this) }

        val model = "gemini-2.5-flash-image"
        val params = GeminiGenerateContentConfig.builder()
            .responseModalities("TEXT", "IMAGE")
            .build()

        val chat = client.chats.create(model, params)

        // expect two images to be generated
        chat.sendMessage("Create a vibrant infographic that explains photosynthesis")
        chat.sendMessage("Update this infographic to be in Japanese")

        val traces = analyzeSpans()
        assertTracesCount(2, traces)

        val trace1 = traces.first()
        val trace2 = traces.last()

        val expectedImage = MediaContentAttributeValues.Data(
            field = "output",
            contentType = "image/png",
            data = null,
        )

        verifyMediaContentUploadAttributes(
            trace1, expected = listOf(
                expectedImage
            )
        )
        // the first image becomes an input
        verifyMediaContentUploadAttributes(
            trace2, expected = listOf(
                expectedImage.copy(field = "input"),
                expectedImage,
            )
        )
    }

    @Test
    fun `test image generated with high-resolution gets traced`() = runTest(timeout = 3.minutes) {
        val client = createGeminiClient(
            timeout = Duration.ofMinutes(3)
        ).apply { instrument(this) }

        val model = "gemini-2.5-flash-image"
        val params = GeminiGenerateContentConfig.builder()
            .responseModalities("TEXT", "IMAGE")
            .imageConfig(
                ImageConfig.builder()
                    .aspectRatio("16:9")
                    .imageSize("4K")
                    .build()
            )
            .build()

        client.models.generateContent(
            model,
            "Generate a cat on the table",
            params,
        )

        validateBasicTracing(model)
        val trace = analyzeSpans().first()

        val expectedImage = MediaContentAttributeValues.Data(
            field = "output",
            contentType = "image/png",
            data = null,
        )
        verifyMediaContentUploadAttributes(
            trace, expected = listOf(
                expectedImage,
            )
        )
    }

    @Test
    fun `test attached audio file gets traced`() = runTest(timeout = 3.minutes) {
        val client = createGeminiClient(
            timeout = Duration.ofMinutes(3)
        ).apply { instrument(this) }

        val model = "gemini-2.5-flash"
        val params = GeminiGenerateContentConfig.builder()
            .responseModalities("TEXT")
            .build()

        val file = MediaSource.File("lofi.mp3", "audio/mp3")

        val prompt = Content.fromParts(
            Part.fromText("Tell me what you hear in the audio file"),
            Part.fromBytes(
                readResource(file.filepath).readAllBytes(),
                file.contentType,
            )
        )

        client.models.generateContent(model, prompt, params)

        validateBasicTracing(model)
        val trace = analyzeSpans().first()

        verifyMediaContentUploadAttributes(
            trace, expected = listOf(
                file.toMediaContentAttributeValues(field = "input"),
            )
        )
    }

    @Test
    fun `test images generated with Imagen API get traced`() = runTest(timeout = 3.minutes) {
        val client = createGeminiClient(
            timeout = Duration.ofMinutes(3)
        ).apply { instrument(this) }

        val model = "imagen-4.0-generate-001"
        val params = GenerateImagesConfig.builder()
            .enhancePrompt(true)
            .language("Korean")
            .numberOfImages(3)
            .build()

        val prompt = "Robot holding a red skateboard with a word 'hello' but in Korean."

        client.models.generateImages(model, prompt, params)

        val traces = analyzeSpans()
        assertTracesCount(1, traces)
        val trace = traces.first()

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

    @Test
    fun `test image editing API gets traced`() = runTest(timeout = 3.minutes) {
        val client = createGeminiClient(
            timeout = Duration.ofMinutes(3)
        ).apply { instrument(this) }

        val model = "imagen-3.0-capability-001"
        val params = EditImageConfig.builder()
            .numberOfImages(2)
            .language("English")
            .aspectRatio("1:1")
            .editMode(EditMode.Known.EDIT_MODE_DEFAULT)
            .build()

        val prompt =
            "I attached two images: Naruto and a comics image in Noir style. Draw Naruto in the style of the given comics image. Modify only the Naruto image, the 2nd one is given as an inspiration and shouldn't be used directly"

        val subjectImage = MediaSource.File("naruto.png", "image/png")
        val styleImage = MediaSource.File("noir-style-image.jpg", "image/jpeg")

        val subject = SubjectReferenceImage.builder()
            .referenceId(1)
            .referenceImage(
                Image.builder()
                    .mimeType(subjectImage.contentType)
                    .imageBytes(readResource(subjectImage.filepath).readAllBytes())
                    .build()
            )
            .config(
                SubjectReferenceConfig.builder()
                    .subjectDescription("Naruto character")
                    .build()
            )
            .build()

        val style = StyleReferenceImage.builder()
            .referenceId(2)
            .referenceImage(
                Image.builder()
                    .mimeType(styleImage.contentType)
                    .imageBytes(readResource(styleImage.filepath).readAllBytes())
                    .build()
            )
            .config(
                StyleReferenceConfig.builder()
                    .styleDescription("Comics Noir Style")
                    .build()
            )
            .build()

        client.models.editImage(
            model,
            prompt,
            listOf(subject, style),
            params,
        )

        val traces = analyzeSpans()
        assertTracesCount(1, traces)
        val trace = traces.first()

        val expectedImage = MediaContentAttributeValues.Data(
            field = "output",
            contentType = "image/png",
            data = null,
        )
        verifyMediaContentUploadAttributes(
            trace, expected = listOf(
                subjectImage.toMediaContentAttributeValues(field = "input"),
                styleImage.toMediaContentAttributeValues(field = "input"),
                expectedImage,
                expectedImage,
            )
        )
    }

    @Test
    fun `test image upscaling API gets traced`() = runTest(timeout = 3.minutes) {
        val client = createGeminiClient(
            timeout = Duration.ofMinutes(3)
        ).apply { instrument(this) }

        val model = "imagen-4.0-upscale-preview"
        val outputMimeType = "image/jpeg"
        val params = UpscaleImageConfig.builder()
            .outputMimeType(outputMimeType)
            .imagePreservationFactor(0.8f)
            .labels(mapOf("label1" to "value1", "label2" to "value2"))
            .build()
        val upscaleFactor = ""

        val image = MediaSource.File("image.jpg", "image/jpeg")
        val inputImage = Image.builder()
            .mimeType(image.contentType)
            .imageBytes(readResource(image.filepath).readAllBytes())
            .build()

        // upscaleImage
        client.models.upscaleImage(model, inputImage, upscaleFactor, params)

        val traces = analyzeSpans()
        assertTracesCount(1, traces)
        val trace = traces.first()

        val expectedImage = MediaContentAttributeValues.Data(
            field = "output",
            contentType = outputMimeType,
            data = null,
        )
        verifyMediaContentUploadAttributes(
            trace, expected = listOf(
                image.toMediaContentAttributeValues(field = "input"),
                expectedImage,
            )
        )
    }
}