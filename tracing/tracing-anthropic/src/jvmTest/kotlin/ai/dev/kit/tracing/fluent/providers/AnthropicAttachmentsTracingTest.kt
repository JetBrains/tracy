package ai.dev.kit.tracing.fluent.providers

import ai.dev.kit.clients.instrument
import ai.dev.kit.tracing.MediaSource
import ai.dev.kit.tracing.asDataUrl
import com.anthropic.core.JsonValue
import com.anthropic.models.messages.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class AnthropicAttachmentsTracingTest : BaseAnthropicTracingTest() {
    @ParameterizedTest
    @MethodSource("provideImagesForUpload")
    fun `test attached image get traced`(image: MediaSource) {
        val client = instrument(createAnthropicClient())

        val params = MessageCreateParams.builder()
            .addUserMessageOfBlockParams(listOf(
                text("Tell me what you see in the image"),
                image(image),
            ))
            .maxTokens(1000L)
            .temperature(0.0)
            .model(Model.CLAUDE_3_7_SONNET_20250219)
            .build()

        val response = client.messages().create(params)
        println(response.content())
    }

    @ParameterizedTest
    @MethodSource("provideFilesForUpload")
    fun `test attached file get traced`(file: MediaSource) {
        val client = instrument(createAnthropicClient())

        val params = MessageCreateParams.builder()
            .addUserMessageOfBlockParams(listOf(
                text("Describe the file attached"),
                file(file),
            ))
            .maxTokens(1000L)
            .temperature(0.0)
            .model(Model.CLAUDE_3_7_SONNET_20250219)
            .build()

        val response = client.messages().create(params)
        println(response.content())
    }

    fun provideImagesForUpload(): Stream<Arguments> {
        return Stream.of(
            Arguments.of(
                MediaSource.File(
                    filepath = "./image.jpg",
                    contentType = "image/jpeg",
                )
            ),
            Arguments.of(MediaSource.Link(CAT_IMAGE_URL)),
        )
    }

    fun provideFilesForUpload(): Stream<Arguments> {
        return Stream.of(
            Arguments.of(MediaSource.File(
                filepath = "./sample.pdf",
                contentType = "application/pdf",
            )),
            Arguments.of(MediaSource.Link(SAMPLE_PDF_FILE_URL))
        )
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
