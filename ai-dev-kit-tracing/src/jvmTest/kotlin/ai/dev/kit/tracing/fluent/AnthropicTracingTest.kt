package ai.dev.kit.tracing.fluent

import ai.dev.kit.instrument
import ai.dev.kit.tracing.BaseOpenTelemetryTracingTest
import ai.dev.kit.tracing.LITELLM_URL
import ai.dev.kit.tracing.autologging.createAnthropicClient
import com.anthropic.client.AnthropicClient
import com.anthropic.client.AnthropicClientImpl
import com.anthropic.core.ClientOptions
import com.anthropic.core.JsonString
import com.anthropic.helpers.MessageAccumulator
import com.anthropic.models.messages.*
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_RESPONSE_FINISH_REASONS
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@Tag("SkipForNonLocal")
class AnthropicTracingTest : BaseOpenTelemetryTracingTest() {
    @Test
    fun `test Anthropic tool auto tracing`() {
        val client = instrument(createAnthropicClient())

        val model = Model.CLAUDE_3_5_SONNET_20240620
        val params = MessageCreateParams.builder()
            .addUserMessage("Use a provided `hi` tool to greet Alex")
            .addTool(createTool("hi"))
            .maxTokens(1000L)
            .temperature(0.0)
            .model(model)
            .build()

        client.messages().create(params)

        val traces = analyzeSpans()

        assertEquals(1, traces.size)
        val trace = traces.firstOrNull()
        assertNotNull(trace)

        // Check tool definitions in the request
        assertEquals("hi", trace.attributes[AttributeKey.stringKey("gen_ai.tool.0.name")])
        assertEquals("custom", trace.attributes[AttributeKey.stringKey("gen_ai.tool.0.type")])
        assertTrue(trace.attributes[AttributeKey.stringKey("gen_ai.tool.0.description")]?.isNotEmpty() == true)
        assertTrue(trace.attributes[AttributeKey.stringKey("gen_ai.tool.0.parameters")]?.isNotEmpty() == true)

        // assert tool use requests when LLM finished with a tool call
        if (trace.attributes[GEN_AI_RESPONSE_FINISH_REASONS]?.contains("tool_use") == true) {
            // expect any of the indices to capture AI's tool call request
            val index = listOf(0, 1).firstOrNull {
                trace.attributes[AttributeKey.stringKey("gen_ai.completion.$it.tool.call.id")]?.isNotEmpty() == true }

            assertEquals("hi", trace.attributes[AttributeKey.stringKey("gen_ai.completion.$index.tool.name")])
            assertEquals("tool_use", trace.attributes[AttributeKey.stringKey("gen_ai.completion.$index.tool.call.type")])
            assertTrue(trace.attributes[AttributeKey.stringKey("gen_ai.completion.$index.tool.call.id")]?.isNotEmpty() == true)
            assertTrue(trace.attributes[AttributeKey.stringKey("gen_ai.completion.$index.tool.arguments")]?.isNotEmpty() == true)
        }
    }

    @Test
    fun `test Anthropic tool auto tracing with a response to a tool call`() {
        val client = instrument(createAnthropicClient())

        val greetTool = createTool("hi")

        val model = Model.CLAUDE_3_5_SONNET_20240620
        val paramsBuilder = MessageCreateParams.builder()
            .addUserMessage("Use a provided `hi` tool to hi Alex")
            .addTool(greetTool)
            .maxTokens(1000L)
            .temperature(0.0)
            .model(model)

        // send a request to AI and expect it requests a tool call execution
        val messageAccumulator = MessageAccumulator.create()
        client.messages().createStreaming(paramsBuilder.build()).use {
            it.stream().forEach(messageAccumulator::accumulate)
        }
        val assistantMessage = messageAccumulator.message()
        paramsBuilder.addMessage(assistantMessage)

        // Find and respond to tool calls
        assistantMessage.content().forEach { block ->
            if (block.isToolUse()) {
                val toolUse = block.toolUse().get()
                // Create a tool output response
                paramsBuilder.addMessage(
                    MessageParam.builder().contentOfBlockParams(listOf(
                        ContentBlockParam.ofToolResult(
                            ToolResultBlockParam.builder()
                                .type(JsonString.of("tool_result"))
                                .toolUseId(toolUse.id())
                                .content("Hello, my dear friend!")
                                .build()
                        )
                    ))
                        .role(MessageParam.Role.USER)
                        .build()
                )
            }
        }

        client.messages().create(paramsBuilder.build())

        // NOTE: the first trace will contain text/event-stream content type, hence it isn't traced fully
        val traces = analyzeSpans()
        assertEquals(2, traces.size)

        val traceWithToolCallResult = traces.lastOrNull()
        assertNotNull(traceWithToolCallResult)

        // there should be three messages: 1) user message, 2) AI response + tool call request, and 3) tool call result
        // we need the latter
        val index = listOf(0, 1, 2).firstOrNull {
            val content = traceWithToolCallResult.attributes[AttributeKey.stringKey("gen_ai.prompt.$it.content")] ?: ""

            val containsToolResult = try {
                val jsonContent = kotlinx.serialization.json.Json.parseToJsonElement(content)
                // content is an array of objects (content blocks)
                // e.g.: [{"tool_use_id":"id","type":"tool_result","content":"text"}]
                jsonContent.jsonArray.firstOrNull()?.jsonObject["type"]?.jsonPrimitive?.content == "tool_result"
            } catch (e: Exception) {
                false
            }
            containsToolResult
        }

        assertTrue(index != null, "Expected to find a tool result in the prompt")

        val content = traceWithToolCallResult.attributes[AttributeKey.stringKey("gen_ai.prompt.$index.content")]!!
        val jsonContent = kotlinx.serialization.json.Json.parseToJsonElement(content).jsonArray.firstOrNull()!!

        assertTrue(jsonContent.jsonObject["tool_use_id"]?.jsonPrimitive?.content?.isNotEmpty() == true)
        assertEquals("tool_result", jsonContent.jsonObject["type"]?.jsonPrimitive?.content)
        assertEquals("Hello, my dear friend!", jsonContent.jsonObject["content"]?.jsonPrimitive?.content)
    }

    @Test
    fun `test Anthropic multiple tools response to tool calls auto tracing`() {
        val client = instrument(createAnthropicClient())

        val greetTool = createTool("hi")
        val goodbyeTool = createTool("goodbye")

        val model = Model.CLAUDE_3_5_SONNET_20240620
        val paramsBuilder = MessageCreateParams.builder()
            .addUserMessage("Use the provided tools to greet Alex, then say goodbye to him. You MUST use the tools!")
            .addTool(greetTool)
            .addTool(goodbyeTool)
            .maxTokens(1000L)
            .temperature(0.0)
            .model(model)

        // send a request to AI and expect it requests tool call executions
        val messageAccumulator = MessageAccumulator.create()
        client.messages().createStreaming(paramsBuilder.build()).use {
            it.stream().forEach(messageAccumulator::accumulate)
        }
        val assistantMessage = messageAccumulator.message()
        paramsBuilder.addMessage(assistantMessage)

        // respond to ALL tool calls with tool_result blocks
        assistantMessage.content().forEach { block ->
            if (block.isToolUse()) {
                val toolUse = block.toolUse().get()
                paramsBuilder.addMessage(
                    MessageParam.builder().contentOfBlockParams(listOf(
                        ContentBlockParam.ofToolResult(
                            ToolResultBlockParam.builder()
                                .type(JsonString.of("tool_result"))
                                .toolUseId(toolUse.id())
                                .content(toolUse.name())
                                .build()
                        )
                    ))
                        .role(MessageParam.Role.USER)
                        .build()
                )
            }
        }

        // final request to AI after providing tool outputs
        client.messages().create(paramsBuilder.build())

        val traces = analyzeSpans()
        assertEquals(2, traces.size)

        val finalTrace = traces.last()

        // Expect two tool_result blocks present among prompts in the final request
        val toolResultCount = (0..5).sumOf { idx ->
            val content = finalTrace.attributes[AttributeKey.stringKey("gen_ai.prompt.$idx.content")] ?: return@sumOf 0
            try {
                val jsonContent = kotlinx.serialization.json.Json.parseToJsonElement(content)
                val arr = jsonContent.jsonArray
                arr.count { it.jsonObject["type"]?.jsonPrimitive?.content == "tool_result" }
            } catch (_: Exception) {
                0
            }
        }
        assertTrue(toolResultCount == 2)
    }

    @Test
    fun `test Anthropic auto tracing`() = runTest {
        val model = Model.CLAUDE_3_5_SONNET_20240620
        val client = instrument(createAnthropicClient())

        val params = MessageCreateParams.builder()
            .maxTokens(1000L)
            .temperature(0.8)
            .addUserMessage("Generate polite greeting and introduce yourself")
            .model(model)
            .build()

        client.messages().create(params)

        val traces = analyzeSpans()

        assertEquals(1, traces.size)
        val trace = traces.firstOrNull()
        assertNotNull(trace)

        assertEquals(
            LITELLM_URL,
            trace.attributes[AttributeKey.stringKey("gen_ai.api_base")]
        )

        assertTrue(
            trace.attributes[AttributeKey.stringKey("gen_ai.response.model")]?.startsWith(model.asString()) == true
        )

        val type = trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.type")]
        assertNotNull(type)
        assertTrue(type.isNotEmpty())

        val text = trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.content")]
        assertNotNull(text)
        assertTrue(text.isNotEmpty())
    }

    @Test
    fun `test Anthropic span error status when requesting non-existent model`() = runTest {
        val client = instrument(createAnthropicClient())

        val params = MessageCreateParams.builder()
            .maxTokens(1000L)
            .temperature(0.8)
            .addUserMessage("Generate polite greeting and introduce yourself")
            .model("[non-existent model!]")
            .build()

        try {
            client.messages().create(params)
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
    fun `test Anthropic span error status when mocking 529 response code`() = runTest {
        val client = instrument(createAnthropicClient())
        val errorMessage = "Server is overloaded, please try again later."

        val serverOverloadedInterceptor = object : Interceptor {
            override fun intercept(chain: Interceptor.Chain): Response {
                val response = chain.proceed(chain.request())
                // see: https://docs.anthropic.com/en/api/errors
                val errorBody = """
                    {
                        "type": "error",
                        "error": {
                            "type": "overloaded_error",
                            "message": "$errorMessage"
                        }
                    }
                """.trimIndent().toResponseBody("application/json".toMediaTypeOrNull())

                return response.newBuilder()
                    .body(errorBody)
                    .code(529)
                    .build()
            }
        }

        installHttpInterceptor(client, interceptor = serverOverloadedInterceptor)

        val params = MessageCreateParams.builder()
            .maxTokens(1000L)
            .temperature(0.8)
            .addUserMessage("Generate polite greeting and introduce yourself")
            .model("[non-existent model!]")
            .build()

        try {
            client.messages().create(params)
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

        assertEquals(errorMessage, trace.attributes[AttributeKey.stringKey("gen_ai.error.message")])
        assertEquals(529, trace.attributes[AttributeKey.longKey("http.status_code")])
    }

    private fun createTool(word: String): Tool {
        return Tool.builder()
            .type(Tool.Type.CUSTOM)
            .name(word)
            .description("Say $word to the user")
            .inputSchema(
                Tool.InputSchema.builder()
                    .type(JsonString.of("object"))
                    .properties(com.anthropic.core.JsonObject.of(mapOf(
                        "name" to com.anthropic.core.JsonObject.of(mapOf(
                            "type" to JsonString.of("string"),
                            "description" to JsonString.of("Say $word to a person")
                        ))
                    )))
                    .required(listOf("name"))
                    .build()
            ).build()
    }

    private fun installHttpInterceptor(client: AnthropicClient, interceptor: Interceptor) {
        val clientOptionsField = AnthropicClientImpl::class.java.getDeclaredField("clientOptions").apply { isAccessible = true }
        val clientOptions = clientOptionsField.get(client)

        val originalHttpClientField = ClientOptions::class.java.getDeclaredField("originalHttpClient").apply { isAccessible = true }
        val originalHttpClient = originalHttpClientField.get(clientOptions)

        val okHttpClientField = com.anthropic.client.okhttp.OkHttpClient::class.java.getDeclaredField("okHttpClient").apply { isAccessible = true }
        val okHttpClient = okHttpClientField.get(originalHttpClient) as okhttp3.OkHttpClient

        val modifiedHttpClient = okHttpClient.newBuilder()
            .addInterceptor(interceptor)
            .build()

        okHttpClientField.set(originalHttpClient, modifiedHttpClient)
    }
}