package ai.dev.kit.tracing.fluent.providers

import ai.dev.kit.clients.instrument
import ai.dev.kit.tracing.MediaSource
import ai.dev.kit.tracing.asDataUrl
import ai.dev.kit.tracing.TracingManager
import ai.dev.kit.tracing.toMediaContentAttributeValues
import ai.dev.kit.tracing.policy.ContentCapturePolicy
import com.anthropic.core.JsonValue
import com.anthropic.models.messages.*
import io.opentelemetry.api.common.AttributeKey
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.time.Duration.Companion.minutes

@Tag("anthropic")
class AnthropicAttachmentsTracingTest : BaseAnthropicTracingTest() {
    private val model = Model.CLAUDE_3_7_SONNET_20250219

    @ParameterizedTest
    @MethodSource("provideImagesForUpload")
    fun `test attached image gets traced`(image: MediaSource) = runTest(timeout = 3.minutes) {
        val client = instrument(
            createAnthropicClient(
                timeout = Duration.ofMinutes(3)
            )
        )

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

    @ParameterizedTest
    @MethodSource("provideContentCapturePolicies")
    fun `test capture policy hides sensitive data for attachments`(policy: ContentCapturePolicy) = runTest(
        timeout = 3.minutes
    ) {
        TracingManager.withCapturingPolicy(policy)

        val client = instrument(createAnthropicClient())

        val image = MediaSource.File("image.jpg", "image/jpeg")

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

        val traces = analyzeSpans()
        assertEquals(1, traces.size)
        val trace = traces.first()

        val prompt = trace.attributes[AttributeKey.stringKey("gen_ai.prompt.0.content")]
        if (!policy.captureInputs) {
            assertEquals("REDACTED", prompt, "User prompt should be redacted")
        } else {
            assertNotEquals("REDACTED", prompt, "User prompt should NOT be redacted")
        }

        val completion = trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.content")]
        if (!policy.captureOutputs) {
            assertEquals("REDACTED", completion, "Assistant completion should be redacted")
        } else {
            assertNotEquals("REDACTED", completion, "Assistant completion should NOT be redacted")
        }

        val expectedUploads = buildList {
            if (policy.captureInputs) {
                add(image.toMediaContentAttributeValues(field = "input"))
            }
        }
        verifyMediaContentUploadAttributes(trace, expected = expectedUploads)
    }

    @Test
    fun `test two attached images get traced`() = runTest(timeout = 3.minutes) {
        val client = instrument(
            createAnthropicClient(
                timeout = Duration.ofMinutes(3)
            )
        )

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
    fun `test attached file gets traced`(file: MediaSource) = runTest(timeout = 3.minutes) {
        val client = instrument(
            createAnthropicClient(
                timeout = Duration.ofMinutes(3)
            )
        )

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
        val client = instrument(
            createAnthropicClient(
                timeout = Duration.ofMinutes(3)
            )
        )

        val file1 = MediaSource.File("sample.pdf", "application/pdf")
        val file2 = MediaSource.Link(SAMPLE_PDF_FILE_URL)

        val params = MessageCreateParams.builder()
            .addUserMessageOfBlockParams(listOf(
                text("Describe the files attached"),
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
        val client = instrument(
            createAnthropicClient(
                timeout = Duration.ofMinutes(3)
            )
        )

        val file = MediaSource.File("sample.pdf", "application/pdf")
        val image = MediaSource.File("image.jpg", "image/jpeg")

        val params = MessageCreateParams.builder()
            .addUserMessageOfBlockParams(listOf(
                text("Describe the attached file and image"),
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

    @Test
    fun `test tracing of document attachment via ContentBlockSource schema`() = runTest(timeout = 3.minutes) {
        /**
         * This test case refers to the following structure of `messages`:
         *   - messages > content: `DocumentBlockParam`
         *      - content > source: `ContentBlockSource`
         *         - source > content: `ContentBlockSourceContent`
         *
         * Example (truncated):
         * ```json
         *     "messages": [
         *         {
         *             "content": [
         *                 { "text": "Analyze the following document", "type": "text" },
         *                 {
         *                     "source": {
         *                         "content": [
         *                             {
         *                                 "source": { "data": "", "media_type": "image/jpeg", "type": "base64" },
         *                                 "type": "image"
         *                             },
         *                             {
         *                                 "source": { "type": "url", "url": "[URL]" },
         *                                 "type": "image"
         *                             }
         *                         ],
         *                         "type": "content"
         *                     },
         *                     "type": "document"
         *                 }
         *             ],
         *             "role": "user"
         *         }
         *     ],
         * ```
         *
         * See [Messages API Docs](https://platform.claude.com/docs/en/api/messages/create)
         */

        val client = instrument(
            createAnthropicClient(
                timeout = Duration.ofMinutes(3)
            )
        )

        val image1 = MediaSource.File("image.jpg", "image/jpeg")
        val image2 = MediaSource.Link(CAT_IMAGE_URL)

        val contentSource = ContentBlockParam.ofDocument(
            DocumentBlockParam.builder()
                .source(
                    ContentBlockSource.builder()
                        .content(ContentBlockSource.Content.ofBlockSource(listOf(
                            ContentBlockSourceContent.ofImage(image(image1).image().get()),
                            ContentBlockSourceContent.ofText(
                                text("See the 2nd image as well").text().get()
                            ),
                            ContentBlockSourceContent.ofImage(image(image2).image().get()),
                        )))
                        .build()
                )
                .build()
        )

        val params = MessageCreateParams.builder()
            .addUserMessageOfBlockParams(listOf(
                text("Analyze the following document"),
                contentSource,
            ))
            .maxTokens(2048L)
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
