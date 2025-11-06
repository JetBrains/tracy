package ai.dev.kit.tracing.fluent.providers

import ai.dev.kit.clients.instrument
import ai.dev.kit.tracing.fluent.providers.BaseOpenAITracingTest.Companion.MediaContentAttributeValues
import com.openai.models.images.ImageGenerateParams
import com.openai.models.images.ImageModel
import io.opentelemetry.api.common.AttributeKey
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

@Tag("openai")
class OpenAIImageGenerationTracingTest : BaseOpenAITracingTest() {
    @ParameterizedTest
    @MethodSource("provideResponseFormats")
    fun `test generate image with different response formats`(
        responseFormat: ImageGenerateParams.ResponseFormat?
    ) = runTest {
        val client = instrument(createOpenAIClient())
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

        verifyMediaContentUploadAttributes(trace, expected = listOf(
            expectedImage,
        ))
    }

    @Test
    fun `test generation of a single JPEG image gets traced`() = runTest(
        timeout = 3.minutes,
    ) {
        val client = instrument(createOpenAIClient())
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

        validateBasicImageTracing(prompt, model)
        val trace = analyzeSpans().first()

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

        verifyMediaContentUploadAttributes(trace, expected = listOf(
            expectedImage,
        ))
    }

    @Test
    fun `test generation of multiple images gets traced`() = runTest {
        val client = instrument(createOpenAIClient())
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

        verifyMediaContentUploadAttributes(trace, expected = listOf(
            expectedImage, expectedImage, expectedImage
        ))
    }

    @Test
    fun `test invalid param 'n=0' gets traced as an error`() = runTest {
        val client = instrument(createOpenAIClient())
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
        } catch (_: Exception) {}

        validateBasicImageTracing(prompt, model)

        val trace = analyzeSpans().first()
        assertEquals("n", trace.attributes[AttributeKey.stringKey("gen_ai.error.param")])
        assertEquals("invalid_request_error", trace.attributes[AttributeKey.stringKey("gen_ai.error.type")])
        assertEquals("integer_below_min_value", trace.attributes[AttributeKey.stringKey("gen_ai.error.code")])
        assertEquals(true, trace.attributes[AttributeKey.stringKey("gen_ai.error.message")]?.isNotEmpty())
    }

    // TODO: implement
    /*
    @Test
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

    fun provideResponseFormats(): Stream<Arguments> {
        return Stream.of(
            Arguments.of(null),
            Arguments.of(ImageGenerateParams.ResponseFormat.URL),
            Arguments.of(ImageGenerateParams.ResponseFormat.B64_JSON),
        )
    }

    private fun validateBasicImageTracing(prompt: String, model: ImageModel) {
        val traces = analyzeSpans()
        Assertions.assertEquals(1, traces.size)
        val trace = traces.first()

        for ((k, v) in trace.attributes.asMap().entries) {
            println("$k: ${v.toString().take(150)}")
        }

        Assertions.assertEquals(prompt, trace.attributes[AttributeKey.stringKey("gen_ai.prompt.0.content")])
        Assertions.assertEquals(
            true,
            trace.attributes[AttributeKey.stringKey("gen_ai.request.model")]?.startsWith(model.asString())
        )
    }
}