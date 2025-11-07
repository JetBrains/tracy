package ai.dev.kit.tracing.fluent.providers

import ai.dev.kit.clients.instrument
import ai.dev.kit.tracing.fluent.providers.BaseOpenAITracingTest.Companion.MediaContentAttributeValues
import ai.dev.kit.tracing.fluent.providers.BaseOpenAITracingTest.Companion.MediaSource
import com.openai.core.MultipartField
import com.openai.models.images.ImageEditParams
import com.openai.models.images.ImageEditParams.Image
import com.openai.models.images.ImageModel
import io.opentelemetry.api.common.AttributeKey
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.io.InputStream
import java.time.Duration
import kotlin.time.Duration.Companion.minutes


@Tag("openai")
class OpenAIImageEditTracingTest : BaseOpenAITracingTest() {
    private val patchedProviderUrl = when {
        // when using LiteLLM, switch to the pass-through
        baseUrl == "https://litellm.labs.jb.gg" -> "$baseUrl/openai"
        else -> llmProviderUrl
    }

    @Test
    fun `test tracing when editing a single image`() = runTest(timeout = 3.minutes) {
        val client = instrument(createOpenAIClient(
            url = patchedProviderUrl,
            timeout = Duration.ofMinutes(3)
        ))

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

        verifyMediaContentUploadAttributes(trace, expected = listOf(
            image.toMediaContentAttributeValues(field = "input"),
            expectedImage,
        ))
    }

    @Test
    fun `test tracing when editing an image with a mask`() = runTest {
        val client = instrument(createOpenAIClient(
            url = patchedProviderUrl,
            timeout = Duration.ofMinutes(3)
        ))

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
        assertEquals(alohaMask.contentType, trace.attributes[AttributeKey.stringKey("gen_ai.request.mask.contentType")])
        assertEquals(alohaMask.filepath, trace.attributes[AttributeKey.stringKey("gen_ai.request.mask.filename")])

        assertEquals("1", trace.attributes[AttributeKey.stringKey("gen_ai.request.n")])

        val expectedImage = MediaContentAttributeValues.Url(
            field = "output",
            url = null,
        )

        verifyMediaContentUploadAttributes(trace, expected = listOf(
            alohaImage.toMediaContentAttributeValues(field = "input"),
            alohaMask.toMediaContentAttributeValues(field = "input"),
            expectedImage,
        ))
    }

    @Test
    fun `test tracing when editing an image with JPEG returned`() = runTest(timeout = 3.minutes) {
        val client = instrument(createOpenAIClient(
            url = patchedProviderUrl,
            timeout = Duration.ofMinutes(3)
        ))

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

        verifyMediaContentUploadAttributes(trace, expected = listOf(
            image.toMediaContentAttributeValues(field = "input"),
            expectedImage,
        ))
    }

    @Test
    fun `test tracing when editing two images`() = runTest(timeout = 3.minutes) {
        val client = instrument(createOpenAIClient(
            url = patchedProviderUrl,
            timeout = Duration.ofMinutes(3)
        ))

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

        verifyMediaContentUploadAttributes(trace, expected = listOf(
            image1.toMediaContentAttributeValues(field = "input"),
            image2.toMediaContentAttributeValues(field = "input"),
            expectedImage,
        ))
    }

    @Test
    fun `test tracing when editing two images with streaming API`() = runTest(timeout = 3.minutes) {
        val client = instrument(createOpenAIClient(
            url = patchedProviderUrl,
            timeout = Duration.ofMinutes(3)
        ))

        val model = ImageModel.GPT_IMAGE_1
        val prompt = "Merge two images!"
        val contentType = "image/png"
        val partialImagesCount = 2
        val size = ImageEditParams.Size._1024X1024

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

        client.images().editStreaming(params).use { events ->
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
        assertFalse(trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.content")].isNullOrEmpty())

        val expectedImage = MediaContentAttributeValues.Data(
            field = "output",
            contentType = contentType,
            data = null,
        )

        verifyMediaContentUploadAttributes(trace, expected = listOf(
            image1.toMediaContentAttributeValues(field = "input"),
            image2.toMediaContentAttributeValues(field = "input"),
            expectedImage,
        ))
    }

    private fun image(filepath: String, contentType: String): MultipartField<Image> {
        val image = readResource(filepath)

        return MultipartField.builder<Image>()
            .value(Image.ofInputStream(image))
            .contentType(contentType)
            .filename(filepath)
            .build()
    }

    private fun images(filepaths: List<String>, contentType: String): MultipartField<Image> {
        val images = buildList {
            for (filepath in filepaths) {
                val image = readResource(filepath)
                add(image)
            }
        }
        return MultipartField.builder<Image>()
            .value(Image.ofInputStreams(images))
            .contentType(contentType)
            .filename(filepaths.first())
            .build()
    }

    private fun validateBasicImageTracing(prompt: String, model: ImageModel) {
        val traces = analyzeSpans()
        assertEquals(1, traces.size)
        val trace = traces.first()

        assertEquals(prompt, trace.attributes[AttributeKey.stringKey("gen_ai.prompt.0.content")])
        assertEquals(true, trace.attributes[AttributeKey.stringKey("gen_ai.request.model")]?.startsWith(model.asString()))
    }
}