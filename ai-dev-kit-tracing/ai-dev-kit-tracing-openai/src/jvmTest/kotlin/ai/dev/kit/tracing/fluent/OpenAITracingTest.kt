package ai.dev.kit.tracing.fluent

import ai.dev.kit.instrument
import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.core.JsonArray
import com.openai.core.JsonString
import com.openai.core.JsonValue
import com.openai.models.ChatModel
import com.openai.models.FunctionDefinition
import com.openai.models.FunctionParameters
import com.openai.models.chat.completions.ChatCompletion
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.chat.completions.ChatCompletionTool
import com.openai.models.chat.completions.ChatCompletionToolMessageParam
import com.openai.models.responses.FunctionTool
import com.openai.models.responses.ResponseCreateParams
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.StatusCode
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import tracing.ai.dev.kit.tracing.BaseOpenTelemetryTracingTest
import tracing.ai.dev.kit.tracing.LITELLM_URL
import java.time.Duration
import kotlin.jvm.optionals.getOrNull
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.text.get

@Tag("SkipForNonLocal")
class OpenAITracingTest : BaseOpenTelemetryTracingTest() {
    fun createLiteLLMClient(): OpenAIClient {
        return OpenAIOkHttpClient.builder()
            .baseUrl(LITELLM_URL)
            .apiKey(System.getenv("LITELLM_API_KEY") ?: error("LITELLM_API_KEY environment variable is not set"))
            .timeout(Duration.ofSeconds(60))
            .build()
    }

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
    fun `test OpenAI responses API auto tracing`() = runTest {
        val client = instrument(createLiteLLMClient())
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

        assertEquals(
            LITELLM_URL,
            trace.attributes[AttributeKey.stringKey("gen_ai.api_base")]
        )
        assertTrue(
            trace.attributes[AttributeKey.stringKey("gen_ai.response.model")]?.startsWith(ChatModel.GPT_4O_MINI.asString()) == true
        )
        val content = trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.content")]
        assertNotNull(content)
        assertTrue(content.isNotEmpty())
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
    fun `test OpenAI responses API span error status when request fails`() = runTest {
        val client = instrument(createLiteLLMClient())
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
        assertEquals(
            LITELLM_URL,
            trace.attributes[AttributeKey.stringKey("gen_ai.api_base")]
        )

        assertTrue(trace.attributes[AttributeKey.stringKey("gen_ai.error.message")]?.isNotEmpty() == true)
        assertTrue(trace.attributes[AttributeKey.stringKey("gen_ai.error.code")]?.isNotEmpty() == true)
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
    fun `test OpenAI responses API tool calls auto tracing`() = runTest {
        val client = instrument(createLiteLLMClient())

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

    fun validateToolCall() {
        val traces = analyzeSpans()

        assertEquals(1, traces.size)
        val trace = traces.firstOrNull()
        assertNotNull(trace)

        assertEquals("hi", trace.attributes[AttributeKey.stringKey("gen_ai.tool.0.name")])
        assertEquals("function", trace.attributes[AttributeKey.stringKey("gen_ai.tool.0.type")])
        assertTrue(trace.attributes[AttributeKey.stringKey("gen_ai.tool.0.description")]?.isNotEmpty() == true)
        assertTrue(trace.attributes[AttributeKey.stringKey("gen_ai.tool.0.parameters")]?.isNotEmpty() == true)

        // if AI called the tool when check its props
        if (trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.finish_reason")] == "tool_calls") {
            assertTrue(trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.tool.0.name")]?.isNotEmpty() == true)
            assertTrue(trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.tool.0.call.id")]?.isNotEmpty() == true)
            assertTrue(trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.tool.0.call.type")]?.isNotEmpty() == true)
            assertTrue(trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.tool.0.arguments")]?.isNotEmpty() == true)
        }
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
                        .toolCallId(toolCall.id())
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
        val client = instrument(createLiteLLMClient())

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
            assertTrue(toolCallResponseTrace.attributes[AttributeKey.stringKey("gen_ai.prompt.2.content")]?.isNotEmpty() == true)
            assertTrue(toolCallResponseTrace.attributes[AttributeKey.stringKey("gen_ai.prompt.2.tool_call_id")]?.isNotEmpty() == true)
        }
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
                        .toolCallId(toolCall.id())
                        .content(toolCall.function().name())
                        .build()
                )
            }

        client.chat().completions().create(paramsBuilder.build())

        validateMultipleToolCallResponseWithInput()
    }

    @Test
    fun `test OpenAI responses API multiple tools response to tool calls auto tracing`() = runTest {
        val client = instrument(createLiteLLMClient())

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
            assertTrue(toolCallRequestTrace.attributes[AttributeKey.stringKey("gen_ai.completion.0.tool.0.name")]?.isNotEmpty() == true)
            assertTrue(toolCallRequestTrace.attributes[AttributeKey.stringKey("gen_ai.completion.0.tool.1.name")]?.isNotEmpty() == true)

            assertEquals("tool", toolCallResponseTrace.attributes[AttributeKey.stringKey("gen_ai.prompt.2.role")])
            assertTrue(toolCallResponseTrace.attributes[AttributeKey.stringKey("gen_ai.prompt.2.content")]?.isNotEmpty() == true)
            assertTrue(toolCallResponseTrace.attributes[AttributeKey.stringKey("gen_ai.prompt.2.tool_call_id")]?.isNotEmpty() == true)

            assertEquals("tool", toolCallResponseTrace.attributes[AttributeKey.stringKey("gen_ai.prompt.3.role")])
            assertTrue(toolCallResponseTrace.attributes[AttributeKey.stringKey("gen_ai.prompt.3.content")]?.isNotEmpty() == true)
            assertTrue(toolCallResponseTrace.attributes[AttributeKey.stringKey("gen_ai.prompt.3.tool_call_id")]?.isNotEmpty() == true)
        }
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

    private fun createTool(word: String): ChatCompletionTool {
        return ChatCompletionTool.builder()
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
}