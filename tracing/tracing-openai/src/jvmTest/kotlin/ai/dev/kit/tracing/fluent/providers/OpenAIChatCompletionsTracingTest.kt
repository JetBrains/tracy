package ai.dev.kit.tracing.fluent.providers

import ai.dev.kit.clients.instrument
import ai.dev.kit.tracing.autologging.createLiteLLMClient
import ai.dev.kit.tracing.fluent.providers.BaseOpenAITracingTest.Companion.MediaSource
import com.openai.models.ChatModel
import com.openai.models.chat.completions.*
import io.opentelemetry.api.common.AttributeKey
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.jvm.optionals.getOrNull
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


@Tag("SkipForNonLocal")
class OpenAIChatCompletionsTracingTest : BaseOpenAITracingTest() {
    /*
    @Test
    fun testSendImg() = runTest {
        val imageResourcePath = "image.jpg"

        // loading image as base64
        val imageData = run {
            val classLoader = Thread.currentThread().contextClassLoader
            val imageFile = classLoader.getResource(imageResourcePath)?.file?.let { File(it) }
                ?: error("Could not find image at $imageResourcePath")
            Base64.getEncoder().encodeToString(imageFile.readBytes())
        }

        val params = MediaUploadParams(
            traceId = "3012f66a6e53834f3c50ad1161d8fc0d",
            field = "input",
            contentType = "image/jpeg",
            data = imageData,
        )

        val result = uploadMediaFileToLangfuse(params)
        assertIs<Result.Success<MediaUploadResponse>>(result)
    }
    */

    @Test
    fun `test OpenAI chat completions auto tracing`() = runTest {
        val client = instrument(createLiteLLMClient())
        val params = ChatCompletionCreateParams.builder()
            .addUserMessage("Generate polite greeting and introduce yourself")
            .model(ChatModel.GPT_4O_MINI).temperature(1.1).build()
        client.chat().completions().create(params)

        validateBasicTracing()
    }

    @Test
    fun `test OpenAI chat completions span error status when request fails`() = runTest {
        val client = instrument(createLiteLLMClient())
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
        val client = instrument(createLiteLLMClient())

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
        val client = instrument(createLiteLLMClient())

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
        val client = instrument(createLiteLLMClient())

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
        val client = createLiteLLMClient()
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
        val client = instrument(createLiteLLMClient())
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

    @Test
    fun `test audio file is extracted and uploaded on Langfuse`() = runTest {
        val filepath = "lofi.wav"
        val model = ChatModel.GPT_4O_AUDIO_PREVIEW
        val prompt = "Tell me what is in the audio file"

        // base64-encoded audio data
        val audioData = loadFileAsBase64Encoded(filepath)
        val client = instrument(createLiteLLMClient())

        val params = ChatCompletionCreateParams.builder()
            .model(model)
            .addUserMessageOfArrayOfContentParts(listOf(
                partAudio(audioData),
                partText(prompt),
            ))
            .build()

        client.chat().completions().create(params)
    }

    @Test
    fun `test PDF file is extracted and uploaded on Langfuse`() = runTest {
        val model = ChatModel.GPT_4O
        val prompt = "Please describe what you see in the PDF file."
        val media = MediaSource.File(
            filepath = "sample.pdf",
            contentType = "application/pdf",
        )

        val client = instrument(createLiteLLMClient())

        val params = ChatCompletionCreateParams.builder()
            .model(model)
            .addUserMessageOfArrayOfContentParts(listOf(
                partFile(media),
                partText(prompt),
            ))
            .build()

        val res = client.chat().completions().create(params)
        println(res)
    }

    @ParameterizedTest
    @MethodSource("provideImagesForUpload")
    fun `test image is extracted and uploaded on Langfuse`(image: MediaSource) = runTest {
        val model = ChatModel.GPT_4O
        val prompt = "Please describe what you see in this image."

        val client = instrument(createLiteLLMClient())

        val params = ChatCompletionCreateParams.builder()
            .model(model)
            .addUserMessageOfArrayOfContentParts(listOf(
                partImage(image),
                partText(prompt),
            ))
            .build()

        // send request
        client.chat().completions().create(params)

        // expect the content of a request to be captures successfully
        validateBasicTracing()

        // TODO: check for media upload attributes & move into a method to use in other test cases
        val trace = analyzeSpans().first()
        val requestContent = trace.attributes[AttributeKey.stringKey("gen_ai.prompt.0.content")]

        assertNotNull(requestContent)
        assertTrue(requestContent.isNotEmpty())

        val json = Json.parseToJsonElement(requestContent)
        // expect the JSON is an array with two elements
        assertIs<kotlinx.serialization.json.JsonArray>(json)
        assertEquals(2, json.jsonArray.size)

        val image = json.jsonArray.firstOrNull { it.jsonObject["type"]!!.jsonPrimitive.content == "image_url" }
        val text = json.jsonArray.firstOrNull { it.jsonObject["type"]!!.jsonPrimitive.content == "text" }

        assertNotNull(image)
        assertNotNull(text)
        assertEquals(prompt, text.jsonObject["text"]!!.jsonPrimitive.content)
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

    /**
     * @param audioData base64-encoded data of an audio file (Note: **NOT** a data URL)
     */
    private fun partAudio(audioData: String) = ChatCompletionContentPart.ofInputAudio(
        ChatCompletionContentPartInputAudio.builder()
            .inputAudio(
                ChatCompletionContentPartInputAudio.InputAudio.builder()
                    .format(ChatCompletionContentPartInputAudio.InputAudio.Format.WAV)
                    .data(audioData)
                    .build()
            )
            .build(),
    )
}
