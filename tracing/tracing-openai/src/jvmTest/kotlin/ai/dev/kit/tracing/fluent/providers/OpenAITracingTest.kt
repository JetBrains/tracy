package ai.dev.kit.tracing.fluent.providers

import ai.dev.kit.clients.instrument
import ai.dev.kit.tracing.BaseOpenTelemetryTracingTest
import ai.dev.kit.tracing.autologging.createOpenAIClient
import com.openai.core.ClientOptions.Companion.PRODUCTION_URL
import com.openai.core.JsonArray
import com.openai.core.JsonString
import com.openai.core.JsonValue
import com.openai.models.ChatModel
import com.openai.models.FunctionDefinition
import com.openai.models.FunctionParameters
import com.openai.models.chat.completions.*
import com.openai.models.responses.FunctionTool
import com.openai.models.responses.ResponseCreateParams
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.StatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.util.*
import java.util.stream.Stream
import kotlin.jvm.optionals.getOrNull
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Tag("openai")
class OpenAITracingTest : BaseOpenTelemetryTracingTest() {
    /**
     * When no value is provided, defaults to [PRODUCTION_URL].
     */
    val llmProviderUrl: String? = System.getenv("LLM_PROVIDER_URL")

    val llmProviderApiKey =
        System.getenv("OPENAI_API_KEY") ?: System.getenv("LLM_PROVIDER_API_KEY")
        ?: error("LLM_PROVIDER_API_KEY environment variable is not set")

    companion object {
        sealed class ImageSource {
            data class File(
                val filepath: String,
                val contentType: String,
            ) : ImageSource()
            data class Link(val url: String) : ImageSource()
        }
    }

    fun provideImagesForUpload(): Stream<Arguments> {
        return Stream.of(
            Arguments.of(ImageSource.File(
                filepath = "./image.jpg",
                contentType = "image/jpeg",
            )),
            Arguments.of(ImageSource.Link(
                "https://images.pexels.com/photos/104827/cat-pet-animal-domestic-104827.jpeg"))
        )
    }

    private fun loadFileAsBase64Encoded(filepath: String): String {
        val classLoader = Thread.currentThread().contextClassLoader
        val file = classLoader.getResource(filepath)?.file?.let { File(it) }
            ?: error("Could not find audio file at $filepath")
        return Base64.getEncoder().encodeToString(file.readBytes())
    }

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
    fun `test audio file is extracted and uploaded on Langfuse`() = runTest {
        val filepath = "lofi.wav"
        val model = ChatModel.GPT_4O_AUDIO_PREVIEW
        val prompt = "Tell me what is in the audio file"

        // base64-encoded audio data
        val audioData = loadFileAsBase64Encoded(filepath)
        val client = instrument(createOpenAIClient(llmProviderUrl, llmProviderApiKey))

        val contentParts = listOf<ChatCompletionContentPart>(
            ChatCompletionContentPart.ofInputAudio(
                ChatCompletionContentPartInputAudio.builder()
                    .inputAudio(
                        ChatCompletionContentPartInputAudio.InputAudio.builder()
                            .format(ChatCompletionContentPartInputAudio.InputAudio.Format.WAV)
                            .data(audioData)
                            .build()
                    )
                    .build(),
            ),
            ChatCompletionContentPart.ofText(
                ChatCompletionContentPartText.builder()
                    .text(prompt)
                    .build()
            )
        )

        val params = ChatCompletionCreateParams.builder()
            .model(model)
            .addUserMessageOfArrayOfContentParts(contentParts)
            .build()

        client.chat().completions().create(params)
    }

    @Test
    fun `test PDF file is extracted and uploaded on Langfuse`() = runTest {
        val model = ChatModel.GPT_4O
        val prompt = "Please describe what you see in the PDF file."
        val filepath = "sample.pdf"

        val fileData = loadFileAsBase64Encoded(filepath)
        val client = instrument(createOpenAIClient(llmProviderUrl, llmProviderApiKey))

        val contentParts = listOf<ChatCompletionContentPart>(
            ChatCompletionContentPart.ofFile(
                ChatCompletionContentPart.File.builder()
                    .file(
                        ChatCompletionContentPart.File.FileObject.builder()
                            .fileData("data:application/pdf;base64,$fileData")
                            .build()
                    )
                    .build()
            ),
            ChatCompletionContentPart.ofText(
                ChatCompletionContentPartText.builder()
                    .text(prompt)
                    .build()
            )
        )

        val params = ChatCompletionCreateParams.builder()
            .model(model)
            .addUserMessageOfArrayOfContentParts(contentParts)
            .build()

        val res = client.chat().completions().create(params)
        println(res)
    }

    @ParameterizedTest
    @MethodSource("provideImagesForUpload")
    fun `test image is extracted and uploaded on Langfuse`(image: ImageSource) = runTest {
        val model = ChatModel.GPT_4O
        val prompt = "Please describe what you see in this image."

        val url = when (image) {
            is ImageSource.File -> {
                // loading image as base64
                val imageData = loadFileAsBase64Encoded(image.filepath)
                "data:${image.contentType};base64,${imageData}"
            }
            is ImageSource.Link -> image.url
        }

        val client = instrument(createOpenAIClient(llmProviderUrl, llmProviderApiKey))

        val contentParts = listOf<ChatCompletionContentPart>(
            ChatCompletionContentPart.ofImageUrl(
                ChatCompletionContentPartImage.builder()
                    .imageUrl(
                        ChatCompletionContentPartImage.ImageUrl.builder().url(url).build()
                    )
                    .build()
            ),
            ChatCompletionContentPart.ofText(
                ChatCompletionContentPartText.builder()
                    .text(prompt)
                    .build()
            )
        )

        val params = ChatCompletionCreateParams.builder()
            .model(model)
            .addUserMessageOfArrayOfContentParts(contentParts)
            .build()

        // send request
        client.chat().completions().create(params)

        // expect the content of a request to be captures successfully
        validateBasicTracing()

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

    @Test
    fun `test OpenAI chat completions auto tracing`() = runTest {
        val client = instrument(createOpenAIClient(llmProviderUrl, llmProviderApiKey))

        val params = ChatCompletionCreateParams.builder()
            .addUserMessage("Generate polite greeting and introduce yourself")
            .model(ChatModel.GPT_4O_MINI).temperature(1.1).build()
        client.chat().completions().create(params)

        validateBasicTracing()
    }

    @Test
    fun `test OpenAI responses API auto tracing`() = runTest {
        val client = instrument(createOpenAIClient(llmProviderUrl, llmProviderApiKey))

        val params = ResponseCreateParams.builder()
            .input("Generate polite greeting and introduce yourself")
            .model(ChatModel.GPT_4O_MINI).temperature(1.1).build()
        client.responses().create(params)

        validateBasicTracing()
    }

    private fun validateBasicTracing() {
        val traces = analyzeSpans()

        assertEquals(1, traces.size)
        val trace = traces.firstOrNull()

        assertNotNull(trace)
        assertTrue(
            (llmProviderUrl ?: PRODUCTION_URL)
                .startsWith(trace.attributes[AttributeKey.stringKey("gen_ai.api_base")].toString())
        )

        val responseModel = trace.attributes[AttributeKey.stringKey("gen_ai.response.model")]
        assertNotNull(responseModel)
        assertTrue(responseModel.startsWith(ChatModel.GPT_4O_MINI.asString()))

        val content = trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.content")]
        assertFalse(content.isNullOrEmpty())
    }

    @Test
    fun `test OpenAI chat completions span error status when request fails`() = runTest {
        val client = instrument(createOpenAIClient(llmProviderUrl, llmProviderApiKey))

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
    fun `test OpenAI responses API span error status when request fails`() = runTest {
        val client = instrument(createOpenAIClient(llmProviderUrl, llmProviderApiKey))

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

    private fun validateErrorStatus() {
        val traces = analyzeSpans()
        val trace = traces.firstOrNull()
        assertNotNull(trace)

        assertEquals(StatusCode.ERROR, trace.status.statusCode)
        assertTrue(
            (llmProviderUrl
                ?: PRODUCTION_URL).startsWith(trace.attributes[AttributeKey.stringKey("gen_ai.api_base")].toString())
        )

        assertFalse(trace.attributes[AttributeKey.stringKey("gen_ai.error.message")].isNullOrEmpty())
        assertFalse(trace.attributes[AttributeKey.stringKey("gen_ai.error.code")].isNullOrEmpty())
    }

    @Test
    fun `test OpenAI chat completions tool calls auto tracing`() = runTest {
        val client = instrument(createOpenAIClient(llmProviderUrl, llmProviderApiKey))

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
    fun `test OpenAI responses API tool calls auto tracing`() = runTest {
        val client = instrument(createOpenAIClient(llmProviderUrl, llmProviderApiKey))

        // defines: `greet(name: String)`
        val greetTool = createFunctionTool("hi")

        val params = ResponseCreateParams.builder()
            .input("Use a given `hi` tool to greet two people: Alex and Aleksandr. You MUST do this with the given tool!")
            .addTool(greetTool)
            .model(ChatModel.GPT_4O_MINI)
            .temperature(0.0)
            .build()

        client.responses().create(params)

        validateToolCall()
    }

    private val ChatCompletionMessageToolCall.id: String
        get() {
            val toolCall = this
            val id = if (toolCall.isFunction()) {
                toolCall.function().get().id()
            } else if (toolCall.isCustom()) {
                toolCall.custom().get().id()
            } else {
                throw IllegalStateException("Cannot extract ID of the tool call $toolCall")
            }
            return id
        }

    private val ChatCompletionMessageToolCall.name: String
        get() {
            val toolCall = this
            val name = if (toolCall.isFunction()) {
                toolCall.function().get().function().name()
            } else if (toolCall.isCustom()) {
                toolCall.custom().get().custom().name()
            } else {
                throw IllegalStateException("Cannot extract name of the tool call $toolCall")
            }
            return name
        }

    fun validateToolCall() {
        val traces = analyzeSpans()

        assertEquals(1, traces.size)
        val trace = traces.firstOrNull()
        assertNotNull(trace)

        assertEquals("hi", trace.attributes[AttributeKey.stringKey("gen_ai.tool.0.name")])
        assertEquals("function", trace.attributes[AttributeKey.stringKey("gen_ai.tool.0.type")])
        assertFalse(trace.attributes[AttributeKey.stringKey("gen_ai.tool.0.description")].isNullOrEmpty())
        assertFalse(trace.attributes[AttributeKey.stringKey("gen_ai.tool.0.parameters")].isNullOrEmpty())

        // if AI called the tool when check its props
        if (trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.finish_reason")] == "tool_calls") {
            assertFalse(trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.tool.0.name")].isNullOrEmpty())
            assertFalse(trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.tool.0.call.id")].isNullOrEmpty())
            assertFalse(trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.tool.0.call.type")].isNullOrEmpty())
            assertFalse(trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.tool.0.arguments")].isNullOrEmpty())
        }
    }

    @Test
    fun `test OpenAI chat completions response to a tool call auto tracing`() = runTest {
        val client = instrument(createOpenAIClient(llmProviderUrl, llmProviderApiKey))

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
    fun `test OpenAI responses API response to a tool call auto tracing`() = runTest {
        val client = instrument(createOpenAIClient(llmProviderUrl, llmProviderApiKey))

        val greetTool = createFunctionTool("hi")

        val userPrompt = "Use the provided `hi` tool to greet Alex. You MUST use the tool!"

        val paramsBuilderFirst = ResponseCreateParams.builder()
            .model(ChatModel.GPT_4O_MINI)
            .addTool(greetTool)
            .input(userPrompt)

        val first = client.responses().create(paramsBuilderFirst.build())

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

    fun validateToolCallResponse() {
        val traces = analyzeSpans()

        assertEquals(2, traces.size)
        // contains AI's request for a tool call
        val toolCallRequestTrace = traces.firstOrNull()
        // contains an answer to a tool call
        val toolCallResponseTrace = traces.lastOrNull()
        assertNotNull(toolCallRequestTrace)
        assertNotNull(toolCallResponseTrace)

        assertEquals("hi", toolCallRequestTrace.attributes[AttributeKey.stringKey("gen_ai.tool.0.name")])
        assertEquals("function", toolCallRequestTrace.attributes[AttributeKey.stringKey("gen_ai.tool.0.type")])

        // if AI called the tool when check its props
        if (toolCallRequestTrace.attributes[AttributeKey.stringKey("gen_ai.completion.0.finish_reason")] == "tool_calls") {
            assertEquals("tool", toolCallResponseTrace.attributes[AttributeKey.stringKey("gen_ai.prompt.2.role")])
            assertFalse(toolCallResponseTrace.attributes[AttributeKey.stringKey("gen_ai.prompt.2.content")].isNullOrEmpty())
            assertFalse(toolCallResponseTrace.attributes[AttributeKey.stringKey("gen_ai.prompt.2.tool_call_id")].isNullOrEmpty())
        }
    }

    @Test
    fun `test OpenAI chat completions multiple tools response to tool calls auto tracing`() = runTest {
        val client = instrument(createOpenAIClient(llmProviderUrl, llmProviderApiKey))

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
    fun `test OpenAI responses API multiple tools response to tool calls auto tracing`() = runTest {
        val client = instrument(createOpenAIClient(llmProviderUrl, llmProviderApiKey))

        val greetTool = createFunctionTool("hi")
        val farewellTool = createFunctionTool("goodbye")

        val userPrompt = "Use the provided tools to greet Alex, then say goodbye to him. You MUST use the tools!"

        val paramsBuilderFirst = ResponseCreateParams.builder()
            .model(ChatModel.GPT_4O_MINI)
            .addTool(greetTool)
            .addTool(farewellTool)
            .input(userPrompt)
        val first = client.responses().create(paramsBuilderFirst.build())
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
                            "hi" -> "hi, Alex!"
                            "goodbye" -> "goodbye, Alex!"
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
            .addTool(greetTool)
            .addTool(farewellTool)
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

    fun validateMultipleToolCallResponseWithInput() {
        val traces = analyzeSpans()
        assertEquals(2, traces.size)

        val toolCallRequestTrace = traces.first()
        val toolCallResponseTrace = traces.last()

        assertEquals("hi", toolCallRequestTrace.attributes[AttributeKey.stringKey("gen_ai.tool.0.name")])
        assertEquals("goodbye", toolCallRequestTrace.attributes[AttributeKey.stringKey("gen_ai.tool.1.name")])

        if (toolCallRequestTrace.attributes[AttributeKey.stringKey("gen_ai.completion.0.finish_reason")] == "tool_calls") {
            assertFalse(toolCallRequestTrace.attributes[AttributeKey.stringKey("gen_ai.completion.0.tool.0.name")].isNullOrEmpty())
            assertFalse(toolCallRequestTrace.attributes[AttributeKey.stringKey("gen_ai.completion.0.tool.1.name")].isNullOrEmpty())

            assertEquals("tool", toolCallResponseTrace.attributes[AttributeKey.stringKey("gen_ai.prompt.2.role")])
            assertFalse(toolCallResponseTrace.attributes[AttributeKey.stringKey("gen_ai.prompt.2.content")].isNullOrEmpty())
            assertFalse(toolCallResponseTrace.attributes[AttributeKey.stringKey("gen_ai.prompt.2.tool_call_id")].isNullOrEmpty())

            assertEquals("tool", toolCallResponseTrace.attributes[AttributeKey.stringKey("gen_ai.prompt.3.role")])
            assertFalse(toolCallResponseTrace.attributes[AttributeKey.stringKey("gen_ai.prompt.3.content")].isNullOrEmpty())
            assertFalse(toolCallResponseTrace.attributes[AttributeKey.stringKey("gen_ai.prompt.3.tool_call_id")].isNullOrEmpty())
        }
    }

    @Test
    fun `test OpenAI auto tracing when instrumentation is off`() = runTest {
        val client = createOpenAIClient(llmProviderUrl, llmProviderApiKey)

        val params = ChatCompletionCreateParams.builder()
            .addUserMessage("Generate polite greeting and introduce yourself")
            .model(ChatModel.GPT_4O_MINI).temperature(1.1).build()
        val result = client.chat().completions().create(params)

        val traces = analyzeSpans()

        assertEquals(0, traces.size)
        assertTrue(result.model().startsWith(ChatModel.GPT_4O_MINI.asString()))
        val content = result.choices().first().message().content().getOrNull()
        assertFalse(content.isNullOrEmpty())
    }

    private fun createTool(word: String): ChatCompletionTool {
        val functionTool = ChatCompletionFunctionTool.builder()
            .type(JsonString.of("function"))
            .function(
                FunctionDefinition.builder()
                    .description("Say $word to the user")
                    .name(word)
                    .parameters(
                        FunctionParameters.builder()
                            .putAdditionalProperty("type", JsonValue.from("object"))
                            .putAdditionalProperty(
                                "properties",
                                JsonValue.from(mapOf("name" to mapOf("type" to "string")))
                            )
                            .putAdditionalProperty("required", JsonArray.of(listOf(JsonString.of("name"))))
                            .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                            .build()
                    )
                    .build()
            )
            .build()

        return ChatCompletionTool.ofFunction(functionTool)
    }

    private fun createFunctionTool(word: String): FunctionTool {
        val schema = JsonValue.from(
            mapOf(
                "type" to "object",
                "properties" to mapOf("name" to mapOf("type" to "string")),
                "required" to listOf("name"),
                "additionalProperties" to false
            )
        )
        return FunctionTool.builder()
            .name(word)
            .description("Say $word to the user")
            .parameters(schema)
            .strict(false)
            .build()
    }

    @Test
    fun `test OpenAI responses API streaming`(): Unit = runTest {
        val client = instrument(createOpenAIClient(llmProviderUrl, llmProviderApiKey))

        val params = ResponseCreateParams.builder()
            .input("Generate polite greeting and introduce yourself")
            .model(ChatModel.GPT_4O_MINI)
            .temperature(0.7)

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
    fun `test OpenAI chat completions streaming`(): Unit = runTest {
        val client = instrument(createOpenAIClient(llmProviderUrl, llmProviderApiKey))

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

    fun validateStreaming(output: String) {
        val traces = analyzeSpans()
        assertEquals(1, traces.size)
        val trace = traces.first()

        val contentType = trace.attributes[AttributeKey.stringKey("gen_ai.completion.content.type")]
        assertNotNull(contentType, "Missing gen_ai.completion.content.type attribute")
        assertTrue(contentType.startsWith("text/event-stream"))

        assertFalse(trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.content")].isNullOrEmpty())
        assertEquals(output, trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.content")])
    }
}
