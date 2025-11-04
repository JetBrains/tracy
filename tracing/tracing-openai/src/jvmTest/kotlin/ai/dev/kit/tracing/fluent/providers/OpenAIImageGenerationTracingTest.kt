package ai.dev.kit.tracing.fluent.providers

import ai.dev.kit.clients.instrument
import ai.dev.kit.tracing.BaseOpenTelemetryTracingTest
import ai.dev.kit.tracing.autologging.createLiteLLMClient
import com.openai.models.images.ImageEditParams
import com.openai.models.images.ImageGenerateParams
import com.openai.models.images.ImageModel
import io.opentelemetry.api.common.AttributeKey
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.InputStream
import java.util.stream.Stream
import kotlin.test.assertEquals


// TODO: images must be traced same as in image attachment API (see special attribute keys).
//       Here, check for those attribute keys as well when rebased.
@Tag("SkipForNonLocal")
class OpenAIImageGenerationTracingTest : BaseOpenTelemetryTracingTest() {
    private fun readFile(filepath: String): InputStream {
        return javaClass.classLoader
            .getResourceAsStream(filepath)
            ?: error("File not found")
    }

    @Test
    fun `test edit image API tracing`() = runTest {
        val client = instrument(createLiteLLMClient())

        val image = readFile("image.png")
        val prompt = "Add a 2nd cat to the image."

        val params = ImageEditParams.builder()
            .prompt(prompt)
            .image(ImageEditParams.Image.ofInputStreams(listOf(image)))
            .model(ImageModel.DALL_E_2)
            .build()

        client.images().edit(params)

        val traces = analyzeSpans()
        assertEquals(1, traces.size)
        val trace = traces.first()

        assertEquals(prompt, trace.attributes[AttributeKey.stringKey("gen_ai.prompt.0.content")])
    }

    @ParameterizedTest
    @MethodSource("provideResponseFormats")
    fun `test generate image with different response formats`(
        responseFormat: ImageGenerateParams.ResponseFormat?
    ) = runTest {
        val client = instrument(createLiteLLMClient())
        val prompt = "generate an image of dog and cat sitting next to each other"

        val params = ImageGenerateParams.builder()
            .prompt(prompt)
            .responseFormat(responseFormat)
            .model(ImageModel.DALL_E_2)
            .size(ImageGenerateParams.Size._256X256)
            .n(1)
            .build()

        client.images().generate(params)
        val traces = analyzeSpans()

        assertEquals(1, traces.size)
        val trace = traces.first()

        assertEquals(prompt, trace.attributes[AttributeKey.stringKey("gen_ai.prompt.0.content")])
    }

    @Test
    fun `test invalid param 'n=0' gets traced as an error`() = runTest {
        val client = instrument(createLiteLLMClient())
        val prompt = "generate an image of a cute cat"

        val params = ImageGenerateParams.builder()
            .prompt(prompt)
            .model(ImageModel.DALL_E_2)
            .size(ImageGenerateParams.Size._256X256)
            .n(0)
            .build()

        try {
            client.images().generate(params)
        } catch (_: Exception) {}

        val traces = analyzeSpans()
        assertEquals(1, traces.size)

        val attributes = traces.first().attributes

        assertEquals(prompt, attributes[AttributeKey.stringKey("gen_ai.prompt.0.content")])
        assertEquals(ImageModel.DALL_E_2.asString(), attributes[AttributeKey.stringKey("gen_ai.request.model")])

        assertEquals("n", attributes[AttributeKey.stringKey("gen_ai.error.param")])
        assertEquals("invalid_request_error", attributes[AttributeKey.stringKey("gen_ai.error.type")])
        assertEquals("400", attributes[AttributeKey.stringKey("gen_ai.error.code")])
        assertEquals(true, attributes[AttributeKey.stringKey("gen_ai.error.message")]?.isNotEmpty())
    }

    @Test
    fun `test generation of multiple images gets traced`() = runTest {
        val client = instrument(createLiteLLMClient())
        val prompt = "generate an image of a cute cat"

        val params = ImageGenerateParams.builder()
            .prompt(prompt)
            .model(ImageModel.DALL_E_2)
            .size(ImageGenerateParams.Size._256X256)
            .n(3)
            .build()

        client.images().generate(params)

        val traces = analyzeSpans()
        assertEquals(1, traces.size)

        val attributes = traces.first().attributes

        assertEquals(prompt, attributes[AttributeKey.stringKey("gen_ai.prompt.0.content")])
        assertEquals(ImageModel.DALL_E_2.asString(), attributes[AttributeKey.stringKey("gen_ai.request.model")])
    }

    /*@Test
    fun `test image generation with streaming API`() {
        val client = instrument(createLiteLLMClient())
        val prompt = "generate an image of dog and cat sitting next to each other"

        val params = ImageGenerateParams.builder()
            .prompt(prompt)
            .model(ImageModel.GPT_IMAGE_1)
            .size(ImageGenerateParams.Size.AUTO)
            .n(1)
            .build()

        // val events = client.images().generateStreaming(params).stream().peek { println(it) }
        val events = client.images().generateStreaming(params)
        events.use {
            // it.stream()
        }

        val traces = analyzeSpans()

        assertEquals(1, traces.size)
        val trace = traces.first()
    }*/

    /*
    val imageUrl = images.data().get().first().url().get()

    val image = HttpClient().use { httpClient ->
        val imageBytes = httpClient.get(imageUrl).readRawBytes()
        // Base64.getEncoder().encodeToString(imageBytes)
        imageBytes
    }

    val path = Paths.get("./image.png")
    val file = if (Files.exists(path)) {
        path
    }
    else {
        Files.createFile(path)
    }

    file.writeBytes(image)
    */

    fun provideResponseFormats(): Stream<Arguments> {
        return Stream.of(
            Arguments.of(null),
            Arguments.of(ImageGenerateParams.ResponseFormat.URL),
            Arguments.of(ImageGenerateParams.ResponseFormat.B64_JSON),
        )
    }
}