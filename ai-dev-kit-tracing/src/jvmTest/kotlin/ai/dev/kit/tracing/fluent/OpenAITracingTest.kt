package ai.dev.kit.tracing.fluent

import ai.dev.kit.instrument
import ai.dev.kit.tracing.BaseOpenTelemetryTracingTest
import ai.dev.kit.tracing.LITELLM_URL
import ai.dev.kit.tracing.autologging.createLiteLLMClient
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
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.StatusCode
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.jvm.optionals.getOrNull
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


@Tag("SkipForNonLocal")
class OpenAITracingTest : BaseOpenTelemetryTracingTest() {
    @Test
    fun `test OpenAI auto tracing`() = runTest {
        val client = instrument(createLiteLLMClient())
        val params = ChatCompletionCreateParams.Companion.builder()
            .addUserMessage("Generate polite greeting and introduce yourself")
            .model(ChatModel.Companion.GPT_4O_MINI).temperature(1.1).build()
        client.chat().completions().create(params)

        val traces = analyzeSpans()

        assertEquals(1, traces.size)
        val trace = traces.firstOrNull()
        assertNotNull(trace)

        assertEquals(
            LITELLM_URL,
            trace.attributes[AttributeKey.stringKey("gen_ai.api_base")]
        )
        assertTrue(
            trace.attributes[AttributeKey.stringKey("gen_ai.response.model")]?.startsWith(ChatModel.Companion.GPT_4O_MINI.asString()) == true
        )
        val content = trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.content")]
        assertNotNull(content)
        assertTrue(content.isNotEmpty())
    }

    @Test
    fun `test OpenAI span error status when request fails`() = runTest {
        val client = instrument(createLiteLLMClient())
        val params = ChatCompletionCreateParams.Companion.builder()
            .addUserMessage("Generate polite greeting and introduce yourself")
            .model(ChatModel.Companion.GPT_4O_MINI)
            // setting invalid temperature
            .temperature(-1000.0)
            .build()

        try {
            client.chat().completions().create(params)
        }
        catch (_: Exception) {
            // suppress
        }

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
    fun `test OpenAI tool calls auto tracing`() = runTest {
        val client = instrument(createLiteLLMClient())

        // defines: `greet(name: String)`
        val greetTool = createGreetTool()

        val params = ChatCompletionCreateParams.Companion.builder()
            .addUserMessage("Use a given `greet` tool to greet two people: Alex and Aleksandr. You MUST do this with the given tool!")
            .addTool(greetTool)
            .model(ChatModel.Companion.GPT_4O_MINI)
            .temperature(0.0)
            .build()

        client.chat().completions().create(params)

        val traces = analyzeSpans()

        assertEquals(1, traces.size)
        val trace = traces.firstOrNull()
        assertNotNull(trace)

        assertEquals("greet", trace.attributes[AttributeKey.stringKey("gen_ai.tool.0.name")])
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
    fun `test OpenAI response to a tool call auto tracing`() = runTest {
        val client = instrument(createLiteLLMClient())

        // defines: `greet(name: String)`
        val greetTool = createGreetTool()

        // See example at:
        // https://github.com/openai/openai-java/blob/main/openai-java-example/src/main/java/com/openai/example/FunctionCallingRawExample.java
        val paramsBuilder = ChatCompletionCreateParams.Companion.builder()
            .addUserMessage("Use a given `greet` tool to greet a person Alex. You MUST do this with the given tool!")
            .addTool(greetTool)
            .model(ChatModel.Companion.GPT_4O_MINI)
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

        val traces = analyzeSpans()

        assertEquals(2, traces.size)
        // contains AI's request for a tool call
        val toolCallRequestTrace = traces.firstOrNull()
        // contains an answer to a tool call
        val toolCallResponseTrace = traces.lastOrNull()
        assertNotNull(toolCallRequestTrace)
        assertNotNull(toolCallResponseTrace)

        assertEquals("greet", toolCallRequestTrace.attributes[AttributeKey.stringKey("gen_ai.tool.0.name")])
        assertEquals("function", toolCallRequestTrace.attributes[AttributeKey.stringKey("gen_ai.tool.0.type")])

        // if AI called the tool when check its props
        if (toolCallRequestTrace.attributes[AttributeKey.stringKey("gen_ai.completion.0.finish_reason")] == "tool_calls") {
            assertEquals("tool", toolCallResponseTrace.attributes[AttributeKey.stringKey("gen_ai.prompt.2.role")])
            assertTrue(toolCallResponseTrace.attributes[AttributeKey.stringKey("gen_ai.prompt.2.content")]?.isNotEmpty() == true)
            assertTrue(toolCallResponseTrace.attributes[AttributeKey.stringKey("gen_ai.prompt.2.tool_call_id")]?.isNotEmpty() == true)
        }
    }

    @Test
    fun `test OpenAI auto tracing when instrumentation is off`() = runTest {
        val client = createLiteLLMClient()
        val params = ChatCompletionCreateParams.Companion.builder()
            .addUserMessage("Generate polite greeting and introduce yourself")
            .model(ChatModel.Companion.GPT_4O_MINI).temperature(1.1).build()
        val result = client.chat().completions().create(params)

        val traces = analyzeSpans()

        assertEquals(0, traces.size)
        assertTrue(
            result.model().startsWith(ChatModel.Companion.GPT_4O_MINI.asString())
        )
        val content = result.choices().first().message().content().getOrNull()
        assertNotNull(content)
        assertTrue(content.isNotEmpty())
    }

    private fun createGreetTool(): ChatCompletionTool {
        return ChatCompletionTool.builder()
            .type(JsonString.of("function"))
            .function(
                FunctionDefinition.builder()
                    .description("Greet the user")
                    .name("greet")
                    .parameters(FunctionParameters.builder()
                        .putAdditionalProperty("type", JsonValue.from("object"))
                        .putAdditionalProperty(
                            "properties", JsonValue.from(mapOf(
                                "name" to mapOf("type" to "string"),
                            )))
                        .putAdditionalProperty("required", JsonArray.of(listOf(
                            JsonString.of("name")
                        )))
                        .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                        .build()
                    )
                    .build()
            ).build()
    }
}
