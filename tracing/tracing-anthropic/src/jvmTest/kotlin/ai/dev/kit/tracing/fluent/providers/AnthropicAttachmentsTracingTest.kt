package ai.dev.kit.tracing.fluent.providers

import ai.dev.kit.clients.instrument
import ai.dev.kit.tracing.MediaSource
import ai.dev.kit.tracing.asDataUrl
import ai.dev.kit.tracing.toMediaContentAttributeValues
import com.anthropic.core.JsonValue
import com.anthropic.models.messages.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.time.Duration.Companion.minutes

@Tag("anthropic")
class AnthropicAttachmentsTracingTest : BaseAnthropicTracingTest() {
    private val model = Model.CLAUDE_3_7_SONNET_20250219

    @ParameterizedTest
    @MethodSource("provideImagesForUpload")
    fun `test attached image gets traced`(image: MediaSource) {
        val client = instrument(createAnthropicClient())

        val params = MessageCreateParams.builder()
            .addUserMessageOfBlockParams(listOf(
                text("Tell me what you see in the image"),
                image(image),
            ))
            .maxTokens(1000L)
            .temperature(0.0)
            .model(model)
            .build()

        client.messages().create(params)

        validateBasicTracing(model)
        val trace = analyzeSpans().first()

        verifyMediaContentUploadAttributes(trace, expected = listOf(
            image.toMediaContentAttributeValues(field = "input")
        ))
    }

    @Test
    fun `test two attached images get traced`() = runTest(timeout = 3.minutes) {
        val client = instrument(createAnthropicClient())

        val image1 = MediaSource.File("image.jpg", "image/jpeg")
        val image2 = MediaSource.Link(CAT_IMAGE_URL)

        val params = MessageCreateParams.builder()
            .addUserMessageOfBlockParams(listOf(
                text("Tell me what you see in the image"),
                image(image1),
                image(image2),
            ))
            .maxTokens(1000L)
            .temperature(0.0)
            .model(model)
            .build()

        client.messages().create(params)

        validateBasicTracing(model)
        val trace = analyzeSpans().first()

        verifyMediaContentUploadAttributes(trace, expected = listOf(
            image1.toMediaContentAttributeValues(field = "input"),
            image2.toMediaContentAttributeValues(field = "input"),
        ))
    }

    @ParameterizedTest
    @MethodSource("provideFilesForUpload")
    fun `test attached file gets traced`(file: MediaSource) {
        val client = instrument(createAnthropicClient())

        val params = MessageCreateParams.builder()
            .addUserMessageOfBlockParams(listOf(
                text("Describe the file attached"),
                file(file),
            ))
            .maxTokens(1000L)
            .temperature(0.0)
            .model(model)
            .build()

        client.messages().create(params)

        validateBasicTracing(model)
        val trace = analyzeSpans().first()

        verifyMediaContentUploadAttributes(trace, expected = listOf(
            file.toMediaContentAttributeValues(field = "input")
        ))
    }

    @Test
    fun `test two attached files get traced`() = runTest(timeout = 3.minutes) {
        val client = instrument(createAnthropicClient())

        val file1 = MediaSource.File("sample.pdf", "application/pdf")
        val file2 = MediaSource.Link(SAMPLE_PDF_FILE_URL)

        val params = MessageCreateParams.builder()
            .addUserMessageOfBlockParams(listOf(
                text("Tell me what you see in the image"),
                file(file1),
                file(file2),
            ))
            .maxTokens(1000L)
            .temperature(0.0)
            .model(model)
            .build()

        client.messages().create(params)

        validateBasicTracing(model)
        val trace = analyzeSpans().first()

        verifyMediaContentUploadAttributes(trace, expected = listOf(
            file1.toMediaContentAttributeValues(field = "input"),
            file2.toMediaContentAttributeValues(field = "input"),
        ))
    }

    @Test
    fun `test attached file and image get traced`() = runTest(timeout = 3.minutes) {
        val client = instrument(createAnthropicClient())

        val file = MediaSource.File("sample.pdf", "application/pdf")
        val image = MediaSource.File("image.jpg", "image/jpeg")

        val params = MessageCreateParams.builder()
            .addUserMessageOfBlockParams(listOf(
                text("Tell me what you see in the image"),
                file(file),
                image(image),
            ))
            .maxTokens(1000L)
            .temperature(0.0)
            .model(model)
            .build()

        client.messages().create(params)

        validateBasicTracing(model)
        val trace = analyzeSpans().first()

        verifyMediaContentUploadAttributes(trace, expected = listOf(
            file.toMediaContentAttributeValues(field = "input"),
            image.toMediaContentAttributeValues(field = "input"),
        ))
    }

    private fun text(content: String): ContentBlockParam {
        return ContentBlockParam.ofText(TextBlockParam.builder()
            .text(content)
            .build()
        )
    }

    private fun file(file: MediaSource): ContentBlockParam {
        val block = when (file) {
            is MediaSource.File -> DocumentBlockParam.builder()
                .source(
                    Base64PdfSource.builder()
                        .mediaType(JsonValue.from(file.contentType))
                        .data(file.asDataUrl().data)
                        .build()
                )
                .build()
            is MediaSource.Link -> DocumentBlockParam.builder()
                .urlSource(file.url)
                .build()
        }
        return ContentBlockParam.ofDocument(block)
    }

    private fun image(image: MediaSource): ContentBlockParam {
        val block = when (image) {
            is MediaSource.File -> ImageBlockParam.builder()
                .source(
                    Base64ImageSource.builder()
                        .mediaType(Base64ImageSource.MediaType.of(image.contentType))
                        .data(image.asDataUrl().data)
                        .build()
                )
                .build()
            is MediaSource.Link -> ImageBlockParam.builder()
                .source(
                    UrlImageSource.builder()
                        .url(image.url)
                        .build()
                )
                .build()
        }
        return ContentBlockParam.ofImage(block)
    }
}
