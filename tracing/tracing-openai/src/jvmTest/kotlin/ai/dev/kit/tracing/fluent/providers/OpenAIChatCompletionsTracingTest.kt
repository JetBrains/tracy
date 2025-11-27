package ai.dev.kit.tracing.fluent.providers

import ai.dev.kit.clients.instrument
import ai.dev.kit.tracing.MediaSource
import ai.dev.kit.tracing.TracingManager
import ai.dev.kit.tracing.loadFileAsBase64Encoded
import ai.dev.kit.tracing.toDataUrl
import ai.dev.kit.tracing.toMediaContentAttributeValues
import com.openai.models.ChatModel
import com.openai.models.chat.completions.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.concurrent.TimeUnit
import kotlin.jvm.optionals.getOrNull
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


@Tag("openai")
class OpenAIChatCompletionsTracingTest : BaseOpenAITracingTest() {
    @Test
    fun `test OpenAI chat completions auto tracing`() = runTest {
        val model = ChatModel.GPT_4O_MINI
        val client = instrument(createOpenAIClient())

        val params = ChatCompletionCreateParams.builder()
            .addUserMessage("Generate polite greeting and introduce yourself")
            .model(model).temperature(1.1).build()
        client.chat().completions().create(params)

        validateBasicTracing(model)
    }

    @Test
    fun `test nested instrumentation calls don't cause duplicative tracing`() = runTest {
        val client = instrument(
            instrument(
                instrument(
                    ai.dev.kit.tracing.autologging.createOpenAIClient(llmProviderUrl, llmProviderApiKey)
                )
            )
        )
        val model = ChatModel.GPT_4O_MINI

        val params = ChatCompletionCreateParams.builder()
            .addUserMessage("Generate polite greeting and introduce yourself")
            .model(model).temperature(1.1).build()
        client.chat().completions().create(params)

        val traces = analyzeSpans()
        assertEquals(1, traces.size)
        validateBasicTracing(model)
    }

    @Test
    fun `test OpenAI chat completions span error status when request fails`() = runTest {
        val client = instrument(createOpenAIClient())
        val params = ChatCompletionCreateParams.builder()
            .addUserMessage("Generate polite greeting and introduce yourself")
            .model(ChatModel.GPT_4O_MINI)
            // setting invalid temperature
            .temperature(-1000.0)
            .build()

        try {
            client.chat().completions().create(params)
        } catch (_: Exception) {
            // suppress
        }

        validateErrorStatus()
    }

    @Test
    fun `test OpenAI chat completions tool calls auto tracing`() = runTest {
        val client = instrument(createOpenAIClient())

        // defines: `greet(name: String)`
        val greetTool = createTool("hi")

        val params = ChatCompletionCreateParams.builder()
            .addUserMessage("Use a given `hi` tool to greet two people: Alex and Aleksandr. You MUST do this with the given tool!")
            .addTool(greetTool)
            .model(ChatModel.GPT_4O_MINI)
            .temperature(0.0)
            .build()

        client.chat().completions().create(params)

        validateToolCall()
    }

    @Test
    fun `test OpenAI chat completions response to a tool call auto tracing`() = runTest {
        val client = instrument(createOpenAIClient())

        // defines: `greet(name: String)`
        val greetTool = createTool("hi")

        // See example at:
        // https://github.com/openai/openai-java/blob/main/openai-java-example/src/main/java/com/openai/example/FunctionCallingRawExample.java
        val paramsBuilder = ChatCompletionCreateParams.builder()
            .addUserMessage("Use a given `hi` tool to greet a person Alex. You MUST do this with the given tool!")
            .addTool(greetTool)
            .model(ChatModel.GPT_4O_MINI)
            .temperature(0.0)

        // expect AI to request a tool call
        client.chat().completions().create(paramsBuilder.build()).choices().stream()
            .map(ChatCompletion.Choice::message)
            .peek(paramsBuilder::addMessage)
            .flatMap { message -> message.toolCalls().stream().flatMap { it.stream() } }
            .forEach { toolCall ->
                // add an answer to a tool call
                paramsBuilder.addMessage(
                    ChatCompletionToolMessageParam.builder()
                        .toolCallId(toolCall.id)
                        .content("Hello! I'm greeting you!")
                        .build()
                )
            }

        // give an answer to a tool call
        client.chat().completions().create(paramsBuilder.build())

        validateToolCallResponse()
    }

    @Test
    fun `test OpenAI chat completions multiple tools response to tool calls auto tracing`() = runTest {
        val client = instrument(createOpenAIClient())

        val greetTool = createTool("hi")
        val farewellTool = createTool("goodbye")

        val paramsBuilder = ChatCompletionCreateParams.builder()
            .addUserMessage("Use the provided tools to greet Alex, then say goodbye to him. You MUST use the tools!")
            .addTool(greetTool)
            .addTool(farewellTool)
            .model(ChatModel.GPT_4O_MINI)
            .temperature(0.0)

        client.chat().completions().create(paramsBuilder.build()).choices().stream()
            .map(ChatCompletion.Choice::message)
            .peek(paramsBuilder::addMessage)
            .flatMap { msg -> msg.toolCalls().stream().flatMap { it.stream() } }
            .forEach { toolCall ->
                paramsBuilder.addMessage(
                    ChatCompletionToolMessageParam.builder()
                        .toolCallId(toolCall.id)
                        .content(toolCall.name)
                        .build()
                )
            }

        client.chat().completions().create(paramsBuilder.build())

        validateMultipleToolCallResponseWithInput()
    }

    @Test
    fun `test OpenAI auto tracing when instrumentation is off`() = runTest {
        val client = createOpenAIClient()
        val params = ChatCompletionCreateParams.builder()
            .addUserMessage("Generate polite greeting and introduce yourself")
            .model(ChatModel.GPT_4O_MINI).temperature(1.1).build()
        val result = client.chat().completions().create(params)

        val traces = analyzeSpans()

        assertEquals(0, traces.size)
        assertTrue(result.model().startsWith(ChatModel.GPT_4O_MINI.asString()))
        val content = result.choices().first().message().content().getOrNull()
        assertNotNull(content)
        assertTrue(content.isNotEmpty())
    }

    @Test
    fun `test OpenAI chat completions streaming`(): Unit = runTest {
        val client = instrument(createOpenAIClient())
        val params = ChatCompletionCreateParams.builder()
            .addUserMessage("Generate polite greeting and introduce yourself")
            .model(ChatModel.GPT_4O_MINI)
            .temperature(0.7)
            .build()

        val sb = StringBuilder()
        client.chat().completions().createStreaming(params).use { stream ->
            stream.stream().forEach { chunk ->
                chunk.choices().forEach { choice ->
                    val delta = choice.delta()
                    delta.content().ifPresent { parts ->
                        parts.forEach { part -> sb.append(part.toString()) }
                    }
                }
            }
        }

        validateStreaming(sb.toString())
    }

    @ParameterizedTest
    @MethodSource("provideImagesForUpload")
    fun `test image is extracted and uploaded on Langfuse`(image: MediaSource) = runTest {
        val model = ChatModel.GPT_4O
        val prompt = "Please describe what you see in this image."

        val client = instrument(createOpenAIClient())

        val params = ChatCompletionCreateParams.builder()
            .model(model)
            .addUserMessageOfArrayOfContentParts(
                listOf(
                    partImage(image),
                    partText(prompt),
                )
            )
            .build()

        // send request
        client.chat().completions().create(params)

        // expect the content of a request to be captures successfully
        validateBasicTracing(model)
        val trace = analyzeSpans().first()
        verifyMediaContentUploadAttributes(
            trace, expected = listOf(
                image.toMediaContentAttributeValues(field = "input")
            )
        )
    }

    @Test
    fun `test audio file is extracted and uploaded on Langfuse`() = runTest {
        val model = ChatModel.GPT_4O_AUDIO_PREVIEW
        val prompt = "Tell me what is in the audio file"
        val filepath = "lofi.wav"

        val client = instrument(createOpenAIClient())

        val params = ChatCompletionCreateParams.builder()
            .model(model)
            .addUserMessageOfArrayOfContentParts(
                listOf(
                    partAudio(filepath),
                    partText(prompt),
                )
            )
            .build()

        client.chat().completions().create(params)

        validateBasicTracing(model)
        val trace = analyzeSpans().first()

        val expectedMedia = MediaSource.File(filepath, "audio/wav")
        verifyMediaContentUploadAttributes(
            trace, expected = listOf(
                expectedMedia.toMediaContentAttributeValues(field = "input")
            )
        )
    }

    @Test
    fun `test PDF file is extracted and uploaded on Langfuse`() = runTest {
        val model = ChatModel.GPT_4O
        val prompt = "Please describe what you see in the PDF file."
        val media = MediaSource.File(
            filepath = "sample.pdf",
            contentType = "application/pdf",
        )

        val client = instrument(createOpenAIClient())

        val params = ChatCompletionCreateParams.builder()
            .model(model)
            .addUserMessageOfArrayOfContentParts(
                listOf(
                    partFile(media),
                    partText(prompt),
                )
            )
            .build()

        client.chat().completions().create(params)

        validateBasicTracing(model)
        val trace = analyzeSpans().first()
        verifyMediaContentUploadAttributes(
            trace, expected = listOf(
                media.toMediaContentAttributeValues(field = "input")
            )
        )
    }

    @Test
    fun `test two images sent simultaneously are both uploaded on Langfuse`() = runTest {
        val model = ChatModel.GPT_4O
        val prompt = "Please describe what you see in both images."

        val client = instrument(createOpenAIClient())

        val images = listOf(
            MediaSource.File(filepath = "image.jpg", contentType = "image/jpeg"),
            MediaSource.Link(CAT_IMAGE_URL),
        )

        // insert both images and the prompt
        val parts: List<ChatCompletionContentPart> = images.map { partImage(it) } + partText(prompt)

        val params = ChatCompletionCreateParams.builder()
            .model(model)
            .addUserMessageOfArrayOfContentParts(parts)
            .build()

        // send request
        client.chat().completions().create(params)

        validateBasicTracing(model)
        val trace = analyzeSpans().first()
        verifyMediaContentUploadAttributes(trace, expected = images.map {
            it.toMediaContentAttributeValues(field = "input")
        })
    }

    @Test
    fun `test OpenAI chat completions auto tracing disable`() = runTest {
        TracingManager.isTracingEnabled = false

        val model = ChatModel.GPT_4O_MINI
        val client = instrument(createOpenAIClient())

        val params = ChatCompletionCreateParams.builder()
            .addUserMessage("Generate polite greeting and introduce yourself")
            .model(model).temperature(1.1).build()

        client.chat().completions().create(params)

        val traces = analyzeSpans()
        assert(traces.isEmpty())
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.MINUTES)
    fun `test several media types sent simultaneously are uploaded on Langfuse`() = runTest {
        val model = ChatModel.GPT_4O
        val prompt = "Please describe every media item attached"

        val client = instrument(createOpenAIClient())

        val image = MediaSource.File("image.jpg", "image/jpeg")
        val file = MediaSource.File("sample.pdf", "application/pdf")

        val params = ChatCompletionCreateParams.builder()
            .model(model)
            .addUserMessageOfArrayOfContentParts(
                listOf(
                    partImage(image),
                    partFile(file),
                    partText(prompt),
                )
            )
            .build()

        // send request
        client.chat().completions().create(params)

        validateBasicTracing(model)
        val trace = analyzeSpans().first()
        verifyMediaContentUploadAttributes(
            trace, expected = listOf(
                image.toMediaContentAttributeValues(field = "input"),
                file.toMediaContentAttributeValues(field = "input"),
            )
        )
    }

    private fun partText(prompt: String) = ChatCompletionContentPart.ofText(
        ChatCompletionContentPartText.builder()
            .text(prompt)
            .build()
    )

    private fun partImage(media: MediaSource): ChatCompletionContentPart {
        val url = when (media) {
            is MediaSource.File -> media.toDataUrl()
            is MediaSource.Link -> media.url
        }
        return ChatCompletionContentPart.ofImageUrl(
            ChatCompletionContentPartImage.builder()
                .imageUrl(
                    ChatCompletionContentPartImage.ImageUrl.builder()
                        .url(url)
                        .build()
                )
                .build()
        )
    }

    private fun partFile(media: MediaSource.File) = ChatCompletionContentPart.ofFile(
        ChatCompletionContentPart.File.builder()
            .file(
                ChatCompletionContentPart.File.FileObject.builder()
                    .fileData(media.toDataUrl())
                    .build()
            )
            .build()
    )

    private fun partAudio(filepath: String): ChatCompletionContentPart {
        val audioData = loadFileAsBase64Encoded(filepath)
        val ext = filepath.substringAfterLast(".")
        val format = when (ext) {
            "wav" -> ChatCompletionContentPartInputAudio.InputAudio.Format.WAV
            "mp3" -> ChatCompletionContentPartInputAudio.InputAudio.Format.MP3
            else -> error("Unsupported file format $ext at $filepath")
        }

        return ChatCompletionContentPart.ofInputAudio(
            ChatCompletionContentPartInputAudio.builder()
                .inputAudio(
                    ChatCompletionContentPartInputAudio.InputAudio.builder()
                        .format(format)
                        .data(audioData)
                        .build()
                )
                .build(),
        )
    }
}
