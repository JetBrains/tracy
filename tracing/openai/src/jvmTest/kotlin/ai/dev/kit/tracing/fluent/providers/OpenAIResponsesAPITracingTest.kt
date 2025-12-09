package ai.dev.kit.tracing.fluent.providers

import ai.dev.kit.clients.instrument
import ai.dev.kit.tracing.MediaSource
import ai.dev.kit.tracing.toDataUrl
import ai.dev.kit.tracing.toMediaContentAttributeValues
import com.openai.core.JsonValue
import com.openai.models.ChatModel
import com.openai.models.responses.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.Duration
import kotlin.time.Duration.Companion.minutes


@Tag("openai")
class OpenAIResponsesAPITracingTest : BaseOpenAITracingTest() {
    @Test
    fun `test OpenAI responses API auto tracing`() = runTest {
        val model = ChatModel.GPT_4O_MINI
        val client = instrument(createOpenAIClient())
        val params = ResponseCreateParams.builder()
            .input("Generate polite greeting and introduce yourself")
            .model(model).temperature(1.1).build()
        client.responses().create(params)

        validateBasicTracing(model)
    }

    @Test
    fun `test OpenAI responses API span error status when request fails`() = runTest {
        val client = instrument(createOpenAIClient())
        val params = ResponseCreateParams.builder()
            .input("Generate polite greeting and introduce yourself")
            .model(ChatModel.GPT_4O_MINI)
            // setting invalid temperature
            .temperature(-1000.0)
            .build()

        try {
            client.responses().create(params)
        } catch (_: Exception) {
            // suppress
        }

        validateErrorStatus()
    }

    @Test
    fun `test OpenAI responses API tool calls auto tracing`() = runTest {
        val client = instrument(createOpenAIClient())

        val toolName = "hi"
        val greetTool = createFunctionTool(toolName)

        val params = ResponseCreateParams.builder()
            .input("Call the `hi` tool with the argument `name` set to 'USER'. Do not output any conversational text; only execute the tool call.")
            .addTool(greetTool)
            .model(ChatModel.GPT_4O_MINI)
            .temperature(0.0)
            .build()

        val response = client.responses().create(params)

        flushTracesAndAssumeToolCalled(response, toolName, Response::containsToolCall)

        validateToolCall()
    }

    @Test
    fun `test OpenAI responses API response to a tool call auto tracing`() = runTest {
        val client = instrument(createOpenAIClient())

        val toolName = "hi"
        val greetTool = createFunctionTool(toolName)

        val userPrompt = "Call the `hi` tool with the argument `name` set to 'USER'. Do not output any conversational text; only execute the tool call."

        val paramsBuilderFirst = ResponseCreateParams.builder()
            .model(ChatModel.GPT_4O_MINI)
            .temperature(0.0)
            .addTool(greetTool)
            .input(userPrompt)

        val first = client.responses().create(paramsBuilderFirst.build())

        flushTracesAndAssumeToolCalled(first, toolName, Response::containsToolCall)

        val toolCalls = first.output().mapNotNull { it.functionCall().orElse(null) }

        val assistantWithToolResults = mapOf(
            "role" to "assistant",
            "content" to (
                    toolCalls.map { call ->
                        mapOf(
                            "type" to "output_text",
                            "tool_use_id" to call.callId(),
                            "text" to "Hello! I'm greeting you!"
                        )
                    }
                    )
        )

        val paramsBuilderSecond = ResponseCreateParams.builder()
            .model(ChatModel.GPT_4O_MINI)
            .temperature(0.0)
            .addTool(greetTool)
            .input(
                JsonValue.from(
                    listOf(
                        mapOf("role" to "user", "content" to userPrompt),
                        assistantWithToolResults
                    )
                )
            )

        client.responses().create(paramsBuilderSecond.build())

        validateToolCallResponse()
    }

    @Test
    fun `test OpenAI responses API multiple tools response to tool calls auto tracing`() = runTest {
        val client = instrument(createOpenAIClient())

        val greetToolName = "hi"
        val greetTool = createFunctionTool(greetToolName)
        val goodbyeToolName = "goodbye"
        val goodbyeTool = createFunctionTool(goodbyeToolName)

        val userPrompt = "Call the `hi` tool with the argument `name` set to 'USER' and `goodbye` with the argument `name` set to 'USER'. Do not output any conversational text; only execute the tool calls."

        val paramsBuilderFirst = ResponseCreateParams.builder()
            .model(ChatModel.GPT_4O_MINI)
            .temperature(0.0)
            .addTool(greetTool)
            .addTool(goodbyeTool)
            .input(userPrompt)

        val first = client.responses().create(paramsBuilderFirst.build())

        flushTracesAndAssumeToolCalled(first, greetToolName, Response::containsToolCall)
        flushTracesAndAssumeToolCalled(first, goodbyeToolName, Response::containsToolCall)

        val toolCalls = first.output().mapNotNull { it.functionCall().orElse(null) }

        val assistantWithToolResults = mapOf(
            "role" to "assistant",
            "content" to (
                    listOf(
                        mapOf(
                            "type" to "output_text",
                            "text" to "Tool results:"
                        )
                    ) + toolCalls.map { call ->
                        val resultText = when (call.name()) {
                            "hi" -> "hi, USER!"
                            "goodbye" -> "goodbye, USER!"
                            else -> "done"
                        }
                        mapOf(
                            "type" to "output_text",
                            "tool_use_id" to call.callId(),
                            "text" to resultText
                        )
                    }
                    )
        )

        val paramsBuilderSecond = ResponseCreateParams.builder()
            .model(ChatModel.GPT_4O_MINI)
            .temperature(0.0)
            .addTool(greetTool)
            .addTool(goodbyeTool)
            .input(
                JsonValue.from(
                    listOf(
                        mapOf("role" to "user", "content" to userPrompt),
                        assistantWithToolResults
                    )
                )
            )

        client.responses().create(paramsBuilderSecond.build())

        validateMultipleToolCallResponseWithInput()
    }

    @Test
    fun `test OpenAI responses API streaming`(): Unit = runTest {
        val client = instrument(createOpenAIClient())

        val params = ResponseCreateParams.builder()
            .input("Generate polite greeting and introduce yourself")
            .model(ChatModel.GPT_4O_MINI)
            .temperature(0.0)

        val sb = StringBuilder()
        client.responses().createStreaming(params.build())
            .use { stream ->
                stream.stream().forEach { event ->
                    event.outputTextDelta().ifPresent { delta ->
                        sb.append(delta.delta())
                    }
                }
            }

        validateStreaming(sb.toString())
    }

    @Test
    fun `test OpenAI responses API additional attributes`() = runTest {
        val client = instrument(createOpenAIClient(llmProviderUrl, llmProviderApiKey))

        val paramsBuilder = ResponseCreateParams.builder()
            .input("Say hi to user")
            .model(ChatModel.GPT_4O_MINI)
            .temperature(0.0)
            .metadata(
                ResponseCreateParams.Metadata.builder()
                    .additionalProperties(mapOf("metadataKey" to JsonValue.from("metadataValue")))
                    .build()
            )
            .additionalBodyProperties(
                mapOf("additionalBodyPropertyKey" to JsonValue.from("additionalBodyPropertyValue"))
            )

        client.responses().create(paramsBuilder.build())
        validateAdditionalAttributes()
    }

    @ParameterizedTest
    @MethodSource("provideImagesForUpload")
    fun `test image is extracted and uploaded on Langfuse`(image: MediaSource) = runTest(timeout = 3.minutes) {
        val model = ChatModel.GPT_4O_MINI
        val prompt = "Describe what you see in the image."

        val client = instrument(createOpenAIClient(
            timeout = Duration.ofMinutes(3)
        ))

        val params = ResponseCreateParams.builder()
            .input(
                inputWith(
                    inputImage(image),
                    inputText(prompt),
                )
            )
            .model(model)
            .temperature(0.0)
            .build()

        client.responses().create(params)

        validateBasicTracing(model)
        val trace = analyzeSpans().first()
        verifyMediaContentUploadAttributes(trace, expected = listOf(
            image.toMediaContentAttributeValues(field = "input"),
        ))
    }

    @ParameterizedTest
    @MethodSource("provideFilesForUpload")
    fun `test PDF file is extracted and uploaded on Langfuse`(file: MediaSource) = runTest(timeout = 3.minutes) {
        val model = ChatModel.GPT_4O_MINI
        val prompt = "Describe what you see in the file"

        val client = instrument(createOpenAIClient(
            timeout = Duration.ofMinutes(3)
        ))

        val params = ResponseCreateParams.builder()
            .input(
                inputWith(
                    inputFile(file),
                    inputText(prompt),
                )
            )
            .model(model)
            .temperature(0.0)
            .build()

        client.responses().create(params)

        validateBasicTracing(model)
        val trace = analyzeSpans().first()
        verifyMediaContentUploadAttributes(trace, expected = listOf(
            file.toMediaContentAttributeValues(field = "input"),
        ))
    }

    @Test
    fun `test two images sent simultaneously are both uploaded on Langfuse`() = runTest(timeout = 3.minutes) {
        val model = ChatModel.GPT_4O_MINI
        val prompt = "Describe what you see in both images"

        val fileImage = MediaSource.File("image.jpg", "image/jpeg")
        val urlImage = MediaSource.Link(CAT_IMAGE_URL)

        val client = instrument(createOpenAIClient(
            timeout = Duration.ofMinutes(3)
        ))

        val params = ResponseCreateParams.builder()
            .input(
                inputWith(
                    inputImage(fileImage),
                    inputImage(urlImage),
                    inputText(prompt),
                )
            )
            .model(model)
            .temperature(0.0)
            .build()

        client.responses().create(params)

        validateBasicTracing(model)
        val trace = analyzeSpans().first()
        verifyMediaContentUploadAttributes(trace, expected = listOf(
            fileImage.toMediaContentAttributeValues(field = "input"),
            urlImage.toMediaContentAttributeValues(field = "input"),
        ))
    }

    @Test
    fun `test several media types sent simultaneously are uploaded on Langfuse`() = runTest(timeout = 3.minutes) {
        val model = ChatModel.GPT_4O
        val prompt = "Describe what you see in the media files attached"

        val image = MediaSource.File("image.jpg", "image/jpeg")
        val localFile = MediaSource.File("sample.pdf", "application/pdf")
        val remoteFile = MediaSource.Link(SAMPLE_PDF_FILE_URL)

        val client = instrument(createOpenAIClient(
            timeout = Duration.ofMinutes(3)
        ))

        val params = ResponseCreateParams.builder()
            .input(
                inputWith(
                    inputImage(image),
                    inputFile(localFile),
                    inputFile(remoteFile),
                    inputText(prompt),
                )
            )
            .model(model)
            .temperature(0.0)
            .build()

        client.responses().create(params)

        validateBasicTracing(model)
        val trace = analyzeSpans().first()

        val media = listOf(image, localFile, remoteFile)
        verifyMediaContentUploadAttributes(trace, expected = media.map {
            it.toMediaContentAttributeValues(field = "input")
        })
    }

    private fun inputWith(vararg content: ResponseInputContent) = ResponseCreateParams.Input
        .ofResponse(listOf(
            ResponseInputItem.ofMessage(
                ResponseInputItem.Message.builder()
                    .content(content.toList())
                    .role(ResponseInputItem.Message.Role.USER)
                    .type(ResponseInputItem.Message.Type.MESSAGE)
                    .build()
            )
        ))

    private fun inputText(prompt: String) = ResponseInputContent.ofInputText(
        ResponseInputText.builder()
            .text(prompt)
            .build()
    )

    private fun inputImage(media: MediaSource): ResponseInputContent {
        val url = when (media) {
            is MediaSource.File -> media.toDataUrl()
            is MediaSource.Link -> media.url
        }
        return ResponseInputContent.ofInputImage(
            ResponseInputImage.builder()
                .imageUrl(url)
                .detail(ResponseInputImage.Detail.AUTO)
                .build()
        )
    }

    private fun inputFile(media: MediaSource): ResponseInputContent {
        val file = ResponseInputFile.builder().let {
            when (media) {
                is MediaSource.File -> {
                    it.fileData(media.toDataUrl())
                    it.filename(media.filepath.substringAfterLast('/'))
                }
                is MediaSource.Link -> it.fileUrl(media.url)
            }
        }.build()

        return ResponseInputContent.ofInputFile(file)
    }
}