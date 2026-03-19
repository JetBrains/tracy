/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.anthropic

import org.jetbrains.ai.tracy.anthropic.clients.instrument
import org.jetbrains.ai.tracy.core.TracingManager
import org.jetbrains.ai.tracy.core.patchOpenAICompatibleClient
import org.jetbrains.ai.tracy.core.policy.ContentCapturePolicy
import com.anthropic.core.JsonString
import com.anthropic.core.JsonValue
import com.anthropic.helpers.MessageAccumulator
import com.anthropic.models.messages.*
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_RESPONSE_FINISH_REASONS
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

@Tag("anthropic")
class AnthropicTracingTest : BaseAnthropicTracingTest() {
    private val model = Model.CLAUDE_SONNET_4_5

    @ParameterizedTest
    @MethodSource("provideContentCapturePolicies")
    fun `test capture policy hides sensitive data`(policy: ContentCapturePolicy) {
        TracingManager.withCapturingPolicy(policy)

        val client = createAnthropicClient().apply { instrument(this) }

        val params = MessageCreateParams.builder()
            .addUserMessage("Use a provided `hi` tool to greet Alex. you MUST use the given tool!")
            .addTool(createTool("hi"))
            .maxTokens(1000L)
            .temperature(0.0)
            .model(model)
            .build()

        client.messages().create(params)

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
        val completion = trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.content")]
        if (completion != null) {
            if (!policy.captureOutputs) {
                assertEquals("REDACTED", completion, "Completion content should be redacted")
            } else {
                assertNotEquals("REDACTED", completion, "Completion content should NOT be redacted")
            }
        }

        // if tool call present, verify redaction of tool call details as outputs
        val finishReasons = trace.attributes[AttributeKey.stringArrayKey("gen_ai.response.finish_reasons")]
        Assumptions.assumeTrue { finishReasons?.contains("tool_use") == true }

        // when completion is null, then the tool call index will be 0,
        // otherwise 1 (i.e., coming after the normal assistant response)
        val toolCallIndex = if (completion == null) 0 else 1

        val toolName = trace.attributes[AttributeKey.stringKey("gen_ai.completion.$toolCallIndex.tool.name")]
        val toolArgs = trace.attributes[AttributeKey.stringKey("gen_ai.completion.$toolCallIndex.tool.arguments")]

        if (!policy.captureOutputs) {
            assertEquals("REDACTED", toolName, "Tool name content should be redacted")
            assertEquals("REDACTED", toolArgs, "Tool arguments content should be redacted")
        } else {
            assertNotEquals("REDACTED", toolName, "Tool name content should NOT be redacted")
            assertNotEquals("REDACTED", toolArgs, "Tool arguments content should NOT be redacted")
        }
    }

    @Test
    fun `test nested instrumentation calls don't cause duplicative tracing`() {
        val client = createAnthropicClient()
            .apply { instrument(this) }
            .apply { instrument(this) }
            .apply { instrument(this) }

        val params = MessageCreateParams.builder()
            .addUserMessage("Say hi!")
            .maxTokens(1000L)
            .temperature(0.0)
            .model(model)
            .build()

        client.messages().create(params)

        val traces = analyzeSpans()
        assertTracesCount(1, traces)
    }

    @Test
    fun `test Anthropic tool auto tracing`() {
        val client = createAnthropicClient().apply { instrument(this) }

        val toolName = "hi"
        val params = MessageCreateParams.builder()
            .addUserMessage("Call the `hi` tool with the argument `name` set to 'USER'. Do not output any conversational text; only execute the tool call.")
            .addTool(createTool(toolName))
            .maxTokens(1000L)
            .temperature(0.0)
            .model(model)
            .build()

        val response = client.messages().create(params)

        flushTracesAndAssumeToolCalled(response, toolName, Message::containsToolCall)

        val traces = analyzeSpans()
        assertTracesCount(1, traces)
        val trace = traces.first()

        // Check tool definitions in the request
        assertEquals("hi", trace.attributes[AttributeKey.stringKey("gen_ai.tool.0.name")])
        assertEquals("custom", trace.attributes[AttributeKey.stringKey("gen_ai.tool.0.type")])
        assertFalse(trace.attributes[AttributeKey.stringKey("gen_ai.tool.0.description")].isNullOrEmpty())
        assertFalse(trace.attributes[AttributeKey.stringKey("gen_ai.tool.0.parameters")].isNullOrEmpty())

        // assert tool use requests when LLM finished with a tool call
        if (trace.attributes[GEN_AI_RESPONSE_FINISH_REASONS]?.contains("tool_use") == true) {
            // expect any of the indices to capture AI's tool call request
            val index = listOf(0, 1).firstOrNull {
                trace.attributes[AttributeKey.stringKey("gen_ai.completion.$it.tool.call.id")]?.isNotEmpty() == true
            }

            assertEquals("hi", trace.attributes[AttributeKey.stringKey("gen_ai.completion.$index.tool.name")])
            assertEquals(
                "tool_use",
                trace.attributes[AttributeKey.stringKey("gen_ai.completion.$index.tool.call.type")]
            )
            assertFalse(trace.attributes[AttributeKey.stringKey("gen_ai.completion.$index.tool.call.id")].isNullOrEmpty())
            assertFalse(trace.attributes[AttributeKey.stringKey("gen_ai.completion.$index.tool.arguments")].isNullOrEmpty())
        }
    }

    @Test
    fun `test Anthropic tool auto tracing with a response to a tool call`() {
        val client = createAnthropicClient().apply { instrument(this) }

        val toolName = "hi"
        val greetTool = createTool(toolName)

        val paramsBuilder = MessageCreateParams.builder()
            .addUserMessage("Call the `hi` tool with the argument `name` set to 'USER'. Do not output any conversational text; only execute the tool call.")
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

        flushTracesAndAssumeToolCalled(assistantMessage, toolName, Message::containsToolCall)

        // Find and respond to tool calls
        assistantMessage.content().forEach { block ->
            if (block.isToolUse()) {
                val toolUse = block.toolUse().get()
                // Create a tool output response
                paramsBuilder.addMessage(
                    MessageParam.builder().contentOfBlockParams(
                        listOf(
                            ContentBlockParam.ofToolResult(
                                ToolResultBlockParam.builder()
                                    .type(JsonString.of("tool_result"))
                                    .toolUseId(toolUse.id())
                                    .content("Hello, my dear friend!")
                                    .build()
                            )
                        )
                    )
                        .role(MessageParam.Role.USER)
                        .build()
                )
            }
        }

        client.messages().create(paramsBuilder.build())

        // NOTE: the first trace will contain text/event-stream content type, hence it isn't traced fully
        val traces = analyzeSpans()
        assertTracesCount(2, traces)

        val traceWithToolCallResult = traces.lastOrNull()
        assertNotNull(traceWithToolCallResult)

        // there should be three messages: 1) user message, 2) AI response + tool call request, and 3) tool call result
        // we need the latter
        val index = listOf(0, 1, 2).firstOrNull {
            val content = traceWithToolCallResult.attributes[AttributeKey.stringKey("gen_ai.prompt.$it.content")] ?: ""

            val containsToolResult = try {
                val jsonContent = Json.parseToJsonElement(content)
                // content is an array of objects (content blocks)
                // e.g.: [{"tool_use_id":"id","type":"tool_result","content":"text"}]
                jsonContent.jsonArray.firstOrNull()?.jsonObject["type"]?.jsonPrimitive?.content == "tool_result"
            } catch (_: Exception) {
                false
            }
            containsToolResult
        }

        assertTrue(index != null, "Expected to find a tool result in the prompt")

        val content = traceWithToolCallResult.attributes[AttributeKey.stringKey("gen_ai.prompt.$index.content")]!!
        val jsonContent = Json.parseToJsonElement(content).jsonArray.firstOrNull()!!

        assertFalse(jsonContent.jsonObject["tool_use_id"]?.jsonPrimitive?.content.isNullOrEmpty())
        assertEquals("tool_result", jsonContent.jsonObject["type"]?.jsonPrimitive?.content)
        assertEquals("Hello, my dear friend!", jsonContent.jsonObject["content"]?.jsonPrimitive?.content)
    }

    @Test
    fun `test Anthropic multiple tools response to tool calls auto tracing`() {
        val client = createAnthropicClient().apply { instrument(this) }

        val greetToolName = "hi"
        val greetTool = createTool(greetToolName)

        val goodbyeToolName = "goodbye"
        val goodbyeTool = createTool(goodbyeToolName)

        val paramsBuilder = MessageCreateParams.builder()
            .addUserMessage("Call the `hi` tool with the argument `name` set to 'USER' and `goodbye` with the argument `name` set to 'USER'. Do not output any conversational text; only execute the tool calls.")
            .addTool(greetTool)
            .addTool(goodbyeTool)
            .maxTokens(1000L)
            .temperature(0.0)
            .model(model)

        fun addToolResults(m: Message) {
            m.content().forEach { block ->
                if (block.isToolUse()) {
                    val toolUse = block.toolUse().get()
                    paramsBuilder.addMessage(
                        MessageParam.builder().contentOfBlockParams(
                            listOf(
                                ContentBlockParam.ofToolResult(
                                    ToolResultBlockParam.builder()
                                        .type(JsonString.of("tool_result"))
                                        .toolUseId(toolUse.id())
                                        .content(toolUse.name())
                                        .build()
                                )
                            )
                        )
                            .role(MessageParam.Role.USER)
                            .build()
                    )
                }
            }
        }

        // send a request to AI and expect it requests tool call executions
        val messageAccumulator = MessageAccumulator.create()
        client.messages().createStreaming(paramsBuilder.build()).use {
            it.stream().forEach(messageAccumulator::accumulate)
        }

        val firstAssistant = messageAccumulator.message()
        paramsBuilder.addMessage(firstAssistant)
        addToolResults(firstAssistant)

        flushTracesAndAssumeToolCalled(firstAssistant, greetToolName, Message::containsToolCall)

        // Model may return both tool calls in the first message or one at a time - handle both cases
        val secondAssistant = client.messages().create(paramsBuilder.build())
        paramsBuilder.addMessage(secondAssistant)
        addToolResults(secondAssistant)

        val bothToolsInFirstMessage = firstAssistant.containsToolCall(goodbyeToolName)
        if (!bothToolsInFirstMessage) {
            flushTracesAndAssumeToolCalled(secondAssistant, goodbyeToolName, Message::containsToolCall)
        }

        client.messages().create(paramsBuilder.build())

        val traces = analyzeSpans()
        assertTracesCount(3, traces)

        val finalTrace = traces.last()

        // Expect two tool_result blocks present among prompts in the final request
        val toolResultCount = (0..5).sumOf { idx ->
            val content = finalTrace.attributes[AttributeKey.stringKey("gen_ai.prompt.$idx.content")] ?: return@sumOf 0
            try {
                val jsonContent = Json.parseToJsonElement(content)
                val arr = jsonContent.jsonArray
                arr.count { it.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "tool_result" }
            } catch (_: Exception) {
                0
            }
        }
        assertEquals(2, toolResultCount)
    }

    @Test
    fun `test Anthropic auto tracing`() = runTest {
        val client = createAnthropicClient().apply { instrument(this) }

        val model = Model.CLAUDE_HAIKU_4_5

        val params = MessageCreateParams.builder()
            .maxTokens(1000L)
            .temperature(0.8)
            .addUserMessage("Generate polite greeting and introduce yourself")
            .model(model)
            .build()

        client.messages().create(params)

        val traces = analyzeSpans()
        assertTracesCount(1, traces)
        val trace = traces.first()

        assertEquals(
            llmProviderUrl,
            trace.attributes[AttributeKey.stringKey("gen_ai.api_base")]
        )

        assertTrue(trace.attributes[AttributeKey.stringKey("gen_ai.response.model")]?.commonPrefixWith(model.asString()) == "claude-haiku-4-5")

        val type = trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.type")]
        assertNotNull(type)
        assertTrue(type.isNotEmpty())

        val text = trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.content")]
        assertNotNull(text)
        assertTrue(text.isNotEmpty())
    }

    @Test
    fun `test Anthropic span error status when requesting non-existent model`() = runTest {
        val client = createAnthropicClient().apply { instrument(this) }

        val params = MessageCreateParams.builder()
            .maxTokens(1000L)
            .temperature(0.8)
            .addUserMessage("Generate polite greeting and introduce yourself")
            .model("[non-existent model!]")
            .build()

        try {
            client.messages().create(params)
        } catch (_: Exception) {
            // suppress
        }

        val traces = analyzeSpans()
        val trace = traces.firstOrNull()
        assertNotNull(trace)

        assertEquals(StatusCode.ERROR, trace.status.statusCode)
        assertEquals(
            llmProviderUrl,
            trace.attributes[AttributeKey.stringKey("gen_ai.api_base")]
        )

        assertFalse(trace.attributes[AttributeKey.stringKey("gen_ai.error.message")].isNullOrEmpty())
    }

    @Test
    fun `test Anthropic span error status when mocking 529 response code`() = runTest {
        val client = createAnthropicClient().apply { instrument(this) }

        val errorMessage = "Server is overloaded, please try again later."

        val serverOverloadedInterceptor = Interceptor { chain ->
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

            response.newBuilder()
                .body(errorBody)
                .code(529)
                .build()
        }

        patchOpenAICompatibleClient(
            client = client,
            interceptor = serverOverloadedInterceptor
        )

        val params = MessageCreateParams.builder()
            .maxTokens(1000L)
            .temperature(0.8)
            .addUserMessage("Generate polite greeting and introduce yourself")
            .model("[non-existent model!]")
            .build()

        try {
            client.messages().create(params)
        } catch (_: Exception) {
            // suppress
        }

        val traces = analyzeSpans()
        val trace = traces.firstOrNull()
        assertNotNull(trace)

        assertEquals(StatusCode.ERROR, trace.status.statusCode)
        assertEquals(
            llmProviderUrl,
            trace.attributes[AttributeKey.stringKey("gen_ai.api_base")]
        )

        assertEquals(errorMessage, trace.attributes[AttributeKey.stringKey("gen_ai.error.message")])
        assertEquals(529, trace.attributes[AttributeKey.longKey("http.status_code")])
    }

    @Test
    fun `test Anthropic streaming`() {
        val client = createAnthropicClient().apply { instrument(this) }

        val params = MessageCreateParams.builder()
            .addUserMessage("Write a haiku about tracing.")
            .maxTokens(1000L)
            .temperature(0.0)
            .model(model)
            .build()

        val sb = StringBuilder()
        client.messages().createStreaming(params).use { stream ->
            stream.stream().forEach { event ->
                event.contentBlockDelta().ifPresent { blockDelta ->
                    blockDelta.delta().text().ifPresent { sb.append(it.text()) }
                }
            }
        }

        val traces = analyzeSpans()
        assertTracesCount(1, traces)
        val trace = traces.first()

        val contentType = trace.attributes[AttributeKey.stringKey("gen_ai.completion.content.type")]
        assertNotNull(contentType)
        assertTrue(contentType.startsWith("text/event-stream"))

        // content
        assertFalse(trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.content")].isNullOrEmpty())
        assertEquals(sb.toString(), trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.content")])

        // role
        assertEquals("assistant", trace.attributes[AttributeKey.stringKey("gen_ai.response.role")])

        // finish reason
        val finishReasons = trace.attributes[AttributeKey.stringArrayKey("gen_ai.response.finish_reasons")]
        assertNotNull(finishReasons)
        assertTrue(finishReasons.isNotEmpty())

        // usage
        val inputTokens = trace.attributes[AttributeKey.longKey("gen_ai.usage.input_tokens")]
        assertNotNull(inputTokens, "Streaming response should have input_tokens")
        assertTrue(inputTokens > 0)

        val outputTokens = trace.attributes[AttributeKey.longKey("gen_ai.usage.output_tokens")]
        assertNotNull(outputTokens, "Streaming response should have output_tokens")
        assertTrue(outputTokens > 0)
    }

    @Test
    fun `test Anthropic additional attributes`() = runTest {
        val client = createAnthropicClient().apply { instrument(this) }

        val paramsBuilder = MessageCreateParams.builder()
            .addUserMessage("Say hi to the user.")
            .maxTokens(1000L)
            .additionalBodyProperties(
                mapOf("additionalBodyPropertyKey" to JsonValue.from("additionalBodyPropertyValue"))
            )
            .model(model)

        client.messages().create(paramsBuilder.build())

        val traces = analyzeSpans()
        val trace = traces.firstOrNull()

        assertEquals(
            "\"additionalBodyPropertyValue\"",
            trace?.attributes?.get(AttributeKey.stringKey("tracy.request.additionalBodyPropertyKey"))
        )
    }
}