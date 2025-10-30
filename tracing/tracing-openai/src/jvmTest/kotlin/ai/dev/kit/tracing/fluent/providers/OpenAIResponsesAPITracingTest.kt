package ai.dev.kit.tracing.fluent.providers

import ai.dev.kit.clients.instrument
import ai.dev.kit.tracing.autologging.createLiteLLMClient
import com.openai.core.JsonValue
import com.openai.models.ChatModel
import com.openai.models.responses.ResponseCreateParams
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test


@Tag("SkipForNonLocal")
class OpenAIResponsesAPITracingTest : BaseOpenAITracingTest() {
    @Test
    fun `test OpenAI responses API auto tracing`() = runTest {
        val client = instrument(createLiteLLMClient())
        val params = ResponseCreateParams.builder()
            .input("Generate polite greeting and introduce yourself")
            .model(ChatModel.GPT_4O_MINI).temperature(1.1).build()
        client.responses().create(params)

        validateBasicTracing()
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

    @Test
    fun `test OpenAI responses API streaming`(): Unit = runTest {
        val client = instrument(createLiteLLMClient())

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
}