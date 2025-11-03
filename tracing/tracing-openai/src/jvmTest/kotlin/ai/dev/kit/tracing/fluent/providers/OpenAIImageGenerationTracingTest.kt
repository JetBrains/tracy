package ai.dev.kit.tracing.fluent.providers

import ai.dev.kit.clients.instrument
import ai.dev.kit.tracing.BaseOpenTelemetryTracingTest
import ai.dev.kit.tracing.autologging.createLiteLLMClient
import com.openai.models.images.ImageGenerateParams
import com.openai.models.images.ImageModel
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import io.opentelemetry.api.common.AttributeKey
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream


@Tag("SkipForNonLocal")
class OpenAIImageGenerationTracingTest : BaseOpenTelemetryTracingTest() {
    // stream=1

    // several images: n=3, n=0

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
    fun `test image generation with streaming API`() {
        val client = instrument(createLiteLLMClient())
        val prompt = "generate an image of dog and cat sitting next to each other"

        val params = ImageGenerateParams.builder()
            .prompt(prompt)
            .model(ImageModel.GPT_IMAGE_1)
            .size(ImageGenerateParams.Size._1024X1024)
            .n(1)
            .build()

        val events = client.images().generateStreaming(params).stream().peek { println(it) }

        val traces = analyzeSpans()

        assertEquals(1, traces.size)
        val trace = traces.first()
    }

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