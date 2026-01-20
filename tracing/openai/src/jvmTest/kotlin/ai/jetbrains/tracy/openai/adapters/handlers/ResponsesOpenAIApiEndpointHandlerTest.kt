package ai.jetbrains.tracy.openai.adapters.handlers

import ai.jetbrains.tracy.openai.clients.instrument
import ai.jetbrains.tracy.test.utils.MediaSource
import ai.jetbrains.tracy.core.tracing.TracingManager
import ai.jetbrains.tracy.test.utils.toDataUrl
import ai.jetbrains.tracy.test.utils.toMediaContentAttributeValues
import ai.jetbrains.tracy.openai.adapters.BaseOpenAITracingTest
import ai.jetbrains.tracy.openai.adapters.containsToolCall
import ai.jetbrains.tracy.core.tracing.policy.ContentCapturePolicy
import com.openai.core.JsonValue
import com.openai.models.ChatModel
import com.openai.models.responses.*
import io.opentelemetry.api.common.AttributeKey
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.Duration
import kotlin.time.Duration.Companion.minutes


@Tag("openai")
class ResponsesOpenAIApiEndpointHandlerTest : BaseOpenAITracingTest() {
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
    fun `test instructions are traced as system prompt when input is a string`() = runTest {
        val model = ChatModel.GPT_4O_MINI
        val prompt1 = "Tell me what you can do"
        val instructions = "You MUST respond that you're a manager at the car sales office"

        val client = instrument(createOpenAIClient())

        val params = ResponseCreateParams.builder()
            .input(prompt1)
            .instructions(instructions)
            .model(model)
            .temperature(0.0)
            .build()

        client.responses().create(params)

        validateBasicTracing(model)
        val trace = analyzeSpans().first()

        // first, instructions are traced as a system prompt
        assertEquals(
            instructions,
            trace.attributes[AttributeKey.stringKey("gen_ai.prompt.0.content")],
            "Instructions should be treated as system prompt"
        )
        assertEquals(
            "system",
            trace.attributes[AttributeKey.stringKey("gen_ai.prompt.0.role")],
            "Instructions should be treated as system prompt"
        )
        // input is traced as a user prompt
        assertEquals(
            prompt1,
            trace.attributes[AttributeKey.stringKey("gen_ai.prompt.1.content")],
            "Input should be treated as user prompt",
        )
        assertEquals(
            "user",
            trace.attributes[AttributeKey.stringKey("gen_ai.prompt.1.role")],
            "Input should be treated as user prompt",
        )
    }

    @Test
    fun `test instructions are traced as system prompt when input is an array`() = runTest {
        val model = ChatModel.GPT_4O_MINI
        val prompt1 = "Tell me what you can do"
        val prompt2 = "What are you selling?"
        val instructions = "You MUST respond that you're a manager at the car sales office"

        val client = instrument(createOpenAIClient())

        val params = ResponseCreateParams.builder()
            .input(inputWith(
                inputText(prompt1),
                inputText(prompt2),
            ))
            .instructions(instructions)
            .model(model)
            .temperature(0.0)
            .build()

        client.responses().create(params)

        validateBasicTracing(model)
        val trace = analyzeSpans().first()

        // first, instructions are traced as a system prompt
        assertEquals(
            instructions,
            trace.attributes[AttributeKey.stringKey("gen_ai.prompt.0.content")],
            "Instructions should be treated as system prompt"
        )
        assertEquals(
            "system",
            trace.attributes[AttributeKey.stringKey("gen_ai.prompt.0.role")],
            "Instructions should be treated as system prompt"
        )

        val tracedPrompts = trace.attributes[AttributeKey.stringKey("gen_ai.prompt.1.content")]
        assertNotNull(tracedPrompts)
        for (prompt in listOf(prompt1, prompt2)) {
            assertTrue(tracedPrompts!!.contains(prompt), "Prompt '$prompt' must be present in traced prompts '$tracedPrompts'")
        }
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
            .input("Call the `$toolName` tool with the argument `name` set to 'USER'. Do not output any conversational text; only execute the tool call.")
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

        val userPrompt = "Call the `$toolName` tool with the argument `name` set to 'USER'. Do not output any conversational text; only execute the tool call."

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

        val userPrompt = "Call the `$greetToolName` tool with the argument `name` set to 'USER' and the `$goodbyeToolName` tool with the argument `name` set to 'USER'. Do not output any conversational text; only execute the tool calls."

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
        // this test is only possible on a LiteLLM pass-through.
        // OpenAI API endpoint throws 400 Bad Request on unconventional properties, unlike LiteLLM, which ignores them
        Assumptions.assumeTrue { llmProviderUrl.startsWith("https://litellm.labs.jb.gg") }

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
    @MethodSource("provideContentCapturePolicies")
    fun `test capture policy hides sensitive data`(policy: ContentCapturePolicy) = runTest {
        TracingManager.withCapturingPolicy(policy)

        val client = instrument(createOpenAIClient())

        val greetTool = createFunctionTool("hi")
        val params = ResponseCreateParams.builder()
            .input("Use a given `hi` tool to greet a person named Alex. You MUST use the given tool!")
            .addTool(greetTool)
            .model(ChatModel.GPT_4O_MINI)
            .temperature(0.0)
            .build()

        client.responses().create(params)

        val traces = analyzeSpans()
        assertTracesCount(1, traces)
        val trace = traces.first()

        // input side
        val prompt = trace.attributes[AttributeKey.stringKey("gen_ai.prompt.0.content")]
        val name = trace.attributes[AttributeKey.stringKey("gen_ai.tool.0.name")]
        val description = trace.attributes[AttributeKey.stringKey("gen_ai.tool.0.description")]
        val parameters = trace.attributes[AttributeKey.stringKey("gen_ai.tool.0.parameters")]

        if (!policy.captureInputs) {
            assertEquals("REDACTED", prompt, "User prompt should be redacted")
            assertEquals("REDACTED", name, "Tool name should be redacted")
            assertEquals("REDACTED", description, "Tool description should be redacted")
            assertEquals("REDACTED", parameters, "Tool parameters should be redacted")
        } else {
            assertNotEquals("REDACTED", prompt, "User prompt should NOT be redacted")
            assertNotEquals("REDACTED", name, "Tool name should NOT be redacted")
            assertNotEquals("REDACTED", description, "Tool description should NOT be redacted")
            assertNotEquals("REDACTED", parameters, "Tool parameters should NOT be redacted")
        }

        // output side
        // assume AI called a tool
        Assumptions.assumeTrue(
            trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.tool_name")] != null
        )

        val toolName = trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.tool_name")]
        val toolArguments = trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.tool_arguments")]

        if (!policy.captureOutputs) {
            assertEquals("REDACTED", toolName, "Tool name content should be redacted")
            assertEquals("REDACTED", toolArguments, "Tool arguments content should be redacted")
        } else {
            assertNotEquals("REDACTED", toolName, "Tool name content should NOT be redacted")
            assertNotEquals("REDACTED", toolArguments, "Tool arguments content should NOT be redacted")
        }
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

        // prompt gets traced
        val content = trace.attributes[AttributeKey.stringKey("gen_ai.prompt.0.content")]
        assertNotNull(content)
        assertTrue(content!!.contains(prompt), "Content attribute must contain '$prompt'")

        verifyMediaContentUploadAttributes(trace, expected = listOf(
            image.toMediaContentAttributeValues(field = "input"),
        ))
    }

    @Test
    fun `test user text messages and attached image get traced`() = runTest(timeout = 3.minutes) {
        val model = ChatModel.GPT_4O_MINI
        val prelude = "You are given an image."
        val prompt = "Describe what you see in the image."

        val image = MediaSource.File(
            filepath = "image.jpg",
            contentType = "image/jpeg",
        )

        val client = instrument(createOpenAIClient(
            timeout = Duration.ofMinutes(3)
        ))

        val params = ResponseCreateParams.builder()
            .input(
                // text -> image -> text
                inputWith(
                    inputText(prelude),
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

        val content = trace.attributes[AttributeKey.stringKey("gen_ai.prompt.0.content")]
        assertNotNull(content)

        // both prompts should exist in the content attribute
        assertTrue(content!!.contains(prelude), "Content attribute must contain '$prelude'")
        assertTrue(content.contains(prompt), "Content attribute must contain '$prompt'")

        verifyMediaContentUploadAttributes(trace, expected = listOf(
            image.toMediaContentAttributeValues(field = "input"),
        ))
    }

    @Test
    fun `test user prompt is sent as a single message in input field`() = runTest {
        val model = ChatModel.GPT_4O_MINI
        val prompt = "How are you?"

        val client = instrument(createOpenAIClient())
        val params = ResponseCreateParams.builder()
            .input(inputWith(inputText(prompt)))
            .model(model)
            .temperature(0.0)
            .build()

        client.responses().create(params)

        validateBasicTracing(model)
        val trace = analyzeSpans().first()

        val content = trace.attributes[AttributeKey.stringKey("gen_ai.prompt.0.content")]
        assertNotNull(content)
        assertEquals(prompt, content, "Content attribute must be equal to '$prompt'")
    }

    @Test
    fun `test single message with multiple text content parts gets traced`() = runTest {
        val model = ChatModel.GPT_4O_MINI
        val prelude = "I want you to do two things:"
        val thing1 = "1. Count from 0 to 10 in a single sentence."
        val thing2 = "2. Write the alphabet."

        val client = instrument(createOpenAIClient())
        val params = ResponseCreateParams.builder()
            .input(
                inputWith(
                    inputText(prelude),
                    inputText(thing1),
                    inputText(thing2),
                )
            )
            .model(model)
            .temperature(0.0)
            .build()

        client.responses().create(params)

        validateBasicTracing(model)
        val trace = analyzeSpans().first()

        val content = trace.attributes[AttributeKey.stringKey("gen_ai.prompt.0.content")]
        assertNotNull(content)

        val chunks = listOf(prelude, thing1, thing2)
        for (chunk in chunks) {
            assertTrue(content!!.contains(chunk), "Content attribute must contain '$chunk'")
        }
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