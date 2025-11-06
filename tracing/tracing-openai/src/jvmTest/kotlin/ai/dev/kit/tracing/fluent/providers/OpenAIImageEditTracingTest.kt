package ai.dev.kit.tracing.fluent.providers

import ai.dev.kit.clients.instrument
import ai.dev.kit.exporters.SupportedMediaContentTypes
import ai.dev.kit.tracing.autologging.createOpenAIClient
import ai.dev.kit.tracing.fluent.providers.BaseOpenAITracingTest.Companion.MediaContentAttributeValues
import com.openai.core.MultipartField
import ai.dev.kit.tracing.fluent.providers.BaseOpenAITracingTest.Companion.MediaSource
import com.openai.models.images.ImageEditParams
import com.openai.models.images.ImageEditParams.Image
import com.openai.models.images.ImageModel
import io.opentelemetry.api.common.AttributeKey
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes
import kotlin.time.DurationUnit


@Tag("openai")
class OpenAIImageEditTracingTest : BaseOpenAITracingTest() {
    @Test
    fun `edit example openai sdk`() = runTest {
        val client = createOpenAIClient(
            llmProviderUrl = "https://api.openai.com/v1",
            llmProviderApiKey = llmProviderApiKey,
        )

        val classloader = Thread.currentThread().getContextClassLoader()
        val filename = "image.png"
        val stream = classloader.getResourceAsStream(filename)
        // val maskFilename = "aloha-mask.png"
        // val maskStream = classloader.getResourceAsStream(maskFilename)

        val editParams: ImageEditParams = ImageEditParams.builder()
            //.responseFormat(ImageEditParams.ResponseFormat.URL) // ImageEditParams.ResponseFormat.B64_JSON
            .image(
                MultipartField.builder<Image>()
                    .value(
                        // Or use `Image.ofInputStreams` and pass a `List` to edit multiple images.
                        Image.ofInputStream(stream)
                    )
                    .contentType("image/png")
                    .filename(filename)
                    .build()
            )
            /*.mask(
                builder.builder<InputStream?>()
                    .value(maskStream)
                    .contentType("image/png")
                    .filename(maskFilename)
                    .build()
            )*/
            .prompt("Add another cat to the image.")
            .model(ImageModel.GPT_IMAGE_1) // ImageModel.GPT_IMAGE_1, DALL_E_2
            .n(1)
            .build()

        client.images().edit(editParams)
    }


    @Test
    fun `test edit image API tracing`() = runTest {
        val client = instrument(createOpenAIClient())

        val model = ImageModel.GPT_IMAGE_1
        val prompt = "Add a 2nd cat to the image"
        val filepath = "image.png"

        val params = ImageEditParams.builder()
            .body(
                ImageEditParams.Body.builder()
                    .prompt(prompt)
                    .image(
                        image(filepath)
                    )
                    .model(model)
                    .build()
            )
            .build()

        client.images().edit(params)

        val traces = analyzeSpans()
        assertEquals(1, traces.size)
        val trace = traces.first()

        assertEquals(prompt, trace.attributes[AttributeKey.stringKey("gen_ai.prompt.0.content")])
    }


    @Test
    @Timeout(value = 3, unit = TimeUnit.MINUTES)
    fun `test tracing when editing two images`() = runTest(timeout = 3.minutes) {
        // val client = instrument(createOpenAIClient())
        val client = instrument(createOpenAIClient(
            llmProviderUrl = "https://api.openai.com/v1",
            llmProviderApiKey = llmProviderApiKey,
            timeout = Duration.ofMinutes(3)
        ))

        val model = ImageModel.GPT_IMAGE_1
        val prompt = "Add a 2nd cat to BOTH images attached!"
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
                    .model(model)
                    .build()
            )
            .build()

        client.images().edit(params)

        val traces = analyzeSpans()
        assertEquals(1, traces.size)
        val trace = traces.first()

        for ((k, v) in trace.attributes.asMap().entries) {
            println("$k: ${v.toString().take(150)}")
        }

        assertEquals(prompt, trace.attributes[AttributeKey.stringKey("gen_ai.prompt.0.content")])

        val expectedImage = MediaContentAttributeValues.Data(
            type = SupportedMediaContentTypes.BASE64.type,
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

    private fun image(filepath: String): MultipartField<Image> {
        val ext = filepath.substringAfterLast(".")
        val image = readResource(filepath)

        return MultipartField.builder<Image>()
            .value(Image.ofInputStream(image))
            .contentType("image/${ext}")
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

    // TODO: what if two images uploaded?
}