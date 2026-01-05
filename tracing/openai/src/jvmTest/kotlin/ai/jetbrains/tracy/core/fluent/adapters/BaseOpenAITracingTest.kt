package ai.jetbrains.tracy.core.fluent.adapters

import ai.jetbrains.tracy.test.utils.BaseAITracingTest
import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.core.ClientOptions.Companion.PRODUCTION_URL
import com.openai.core.JsonArray
import com.openai.core.JsonString
import com.openai.core.JsonValue
import com.openai.models.ChatModel
import com.openai.models.FunctionDefinition
import com.openai.models.FunctionParameters
import com.openai.models.chat.completions.ChatCompletion
import com.openai.models.chat.completions.ChatCompletionFunctionTool
import com.openai.models.chat.completions.ChatCompletionMessageToolCall
import com.openai.models.chat.completions.ChatCompletionTool
import com.openai.models.responses.FunctionTool
import com.openai.models.responses.Response
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.StatusCode
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.TestInstance
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseOpenAITracingTest : BaseAITracingTest() {
    protected val llmProviderApiKey = System.getenv("OPENAI_API_KEY")
        ?: System.getenv("LLM_PROVIDER_API_KEY")
        ?: error("LLM_PROVIDER_API_KEY environment variable is not set")

    /**
     * When no value is provided, defaults to [PRODUCTION_URL].
     *
     * When LiteLLM is used as a provider, prefer [patchedProviderUrl].
     */
    protected val llmProviderUrl: String = System.getenv("LLM_PROVIDER_URL") ?: PRODUCTION_URL

    /**
     * When LiteLLM is used as a provider, the API URL gets changed to the
     * OpenAI-oriented pass-through endpoint.
     *
     * See [LiteLLM: Create Pass Through Endpoints](https://docs.litellm.ai/docs/proxy/pass_through)
     */
    protected val patchedProviderUrl = when (val baseUrl = llmProviderUrl.removeSuffix("/v1")) {
        // TODO: remove direct use of litellm
        // when using LiteLLM, switch to the pass-through
        "https://litellm.labs.jb.gg" -> "$baseUrl/openai"
        else -> llmProviderUrl
    }

    protected open fun createOpenAIClient(
        url: String? = llmProviderUrl,
        apiKey: String = llmProviderApiKey,
        timeout: Duration = Duration.ofSeconds(60)
    ): OpenAIClient {
        return OpenAIOkHttpClient.builder()
            .baseUrl(url)
            .apiKey(apiKey)
            .timeout(timeout)
            .build()
    }

    protected fun validateBasicTracing(model: ChatModel) {
        validateBasicTracing(llmProviderUrl, model.asString())
    }

    protected fun validateErrorStatus() {
        val traces = analyzeSpans()
        val trace = traces.firstOrNull()
        assertNotNull(trace)

        assertEquals(StatusCode.ERROR, trace.status.statusCode)
        assertTrue(llmProviderUrl.startsWith(trace.attributes[AttributeKey.stringKey("gen_ai.api_base")].toString()))

        assertFalse(trace.attributes[AttributeKey.stringKey("gen_ai.error.message")].isNullOrEmpty())
        assertFalse(trace.attributes[AttributeKey.stringKey("gen_ai.error.code")].isNullOrEmpty())
    }

    protected fun validateToolCall() {
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

    protected fun validateToolCallResponse() {
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

        // if AI called the tool, then check its props
        if (toolCallRequestTrace.attributes[AttributeKey.stringKey("gen_ai.completion.0.finish_reason")] == "tool_calls") {
            assertEquals("tool", toolCallResponseTrace.attributes[AttributeKey.stringKey("gen_ai.prompt.2.role")])
            assertFalse(toolCallResponseTrace.attributes[AttributeKey.stringKey("gen_ai.prompt.2.content")].isNullOrEmpty())
            assertFalse(toolCallResponseTrace.attributes[AttributeKey.stringKey("gen_ai.prompt.2.tool_call_id")].isNullOrEmpty())
        }
    }

    protected fun validateMultipleToolCallResponseWithInput() {
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

    protected fun validateAdditionalAttributes() {
        val traces = analyzeSpans()
        val trace = traces.firstOrNull()

        assertEquals(
            "{\"metadataKey\":\"metadataValue\"}",
            trace?.attributes?.get(AttributeKey.stringKey("tracy.request.metadata"))
        )
        assertEquals(
            "\"additionalBodyPropertyValue\"",
            trace?.attributes?.get(AttributeKey.stringKey("tracy.request.additionalBodyPropertyKey"))
        )
    }

    protected fun createChatCompletionTool(word: String): ChatCompletionTool {
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

    protected fun createFunctionTool(word: String): FunctionTool {
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

    protected fun validateStreaming(output: String) {
        val traces = analyzeSpans()
        assertEquals(1, traces.size)
        val trace = traces.first()

        val contentType = trace.attributes[AttributeKey.stringKey("gen_ai.completion.content.type")]
        assertNotNull(contentType, "Missing gen_ai.completion.content.type attribute")
        assertTrue(contentType.startsWith("text/event-stream"))

        assertFalse(trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.content")].isNullOrEmpty())
        assertEquals(output, trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.content")])
    }

    protected val ChatCompletionMessageToolCall.id: String
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

    /**
     * Assumes that the provided URL points to the expected OpenAI production endpoint.
     * Uses JUnit assumptions to enforce this condition during testing.
     *
     * @param url The URL to be validated as an OpenAI production endpoint.
     */
    protected fun assumeOpenAIEndpoint(url: String) {
        Assumptions.assumeTrue(url.startsWith(PRODUCTION_URL))
    }
}

internal val ChatCompletionMessageToolCall.name: String
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

internal fun Response.containsToolCall(toolName: String): Boolean {
    return output().any { item ->
        item.functionCall()
            .map { call -> call.name() == toolName }
            .orElse(false)
    }
}

internal fun ChatCompletion.containsToolCall(toolName: String): Boolean {
    return choices().any { choice ->
        choice.message().toolCalls()
            .map { toolCalls -> toolCalls.any { it.name == toolName } }
            .orElse(false)
    }
}