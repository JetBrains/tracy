/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.openai.clients

import com.openai.client.OpenAIClient
import com.openai.core.ClientOptions.Companion.PRODUCTION_URL
import com.openai.models.ChatModel
import com.openai.models.chat.completions.ChatCompletion
import com.openai.models.chat.completions.ChatCompletionCreateParams
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_MODEL
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_SYSTEM
import org.jetbrains.ai.tracy.core.TracingManager
import org.jetbrains.ai.tracy.core.instrumentation.processor.withSpan
import org.jetbrains.ai.tracy.openai.adapters.BaseOpenAITracingTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Tag("openai")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpenAIClientTest : BaseOpenAITracingTest() {
    @Test
    fun testChat() {
        val client = createOpenAIClient().apply { instrument(this) }

        val model = ChatModel.GPT_4O_MINI.asString()
        val systemMessage = "You are a helpful assistant!"
        val userMessage = "Tell me what model are you!"
        val temperature = 1.0

        val result = callChat(client, model, systemMessage, userMessage, temperature)
        val message = result.choices().first().message().content().get()

        analyzeSpans().let { spans ->
            assertTrue("Exactly one span is created") { spans.size == 1 }

            val span = spans.first()
            val attributes = span.attributes.asMap()

            assertEquals("OpenAI-generation", span.name)
            assertEquals(model, attributes[GEN_AI_REQUEST_MODEL])
            assertEquals("openai", attributes[GEN_AI_SYSTEM])
            assertTrue(
                (llmProviderUrl
                    ?: PRODUCTION_URL).startsWith(attributes[AttributeKey.stringKey("gen_ai.api_base")].toString())
            )
            assertEquals("system", attributes[AttributeKey.stringKey("gen_ai.prompt.0.role")])
            assertEquals(systemMessage, attributes[AttributeKey.stringKey("gen_ai.prompt.0.content")])
            assertEquals("user", attributes[AttributeKey.stringKey("gen_ai.prompt.1.role")])
            assertEquals(userMessage, attributes[AttributeKey.stringKey("gen_ai.prompt.1.content")])
            assertEquals(24L, attributes[AttributeKey.longKey("gen_ai.usage.input_tokens")])
            assertEquals(
                temperature,
                attributes[AttributeKey.doubleKey("gen_ai.request.temperature")] as Double,
                absoluteTolerance = 0.00001
            )

            assertEquals("assistant", attributes[AttributeKey.stringKey("gen_ai.completion.0.role")])
            assertEquals("\"$message\"", attributes[AttributeKey.stringKey("gen_ai.completion.0.content")])
            assertEquals("stop", attributes[AttributeKey.stringKey("gen_ai.completion.0.finish_reason")])

            assertTrue { attributes[AttributeKey.longKey("gen_ai.usage.output_tokens")] as Long > 0 }
        }
    }

    @Test
    fun testNestedChat() {
        val client = createOpenAIClient().apply { instrument(this) }

        chatCallingFunction(client)

        analyzeSpans().let { spans ->
            assertTrue("Two spans are created") { spans.size == 2 }

            val spanMap = spans.groupBy { it.name }.mapValues { it.value.first() }
            val outerSpan = spanMap["custom call"]
            val llmSpan = spanMap["OpenAI-generation"]

            assertNotNull(outerSpan)
            assertNotNull(llmSpan)

            assertEquals(llmSpan.parentSpanId, outerSpan.spanId, "LLM span is a child of the outer span")
        }
    }

    @Test
    fun testWithSpan() {
        val client = createOpenAIClient().apply { instrument(this) }

        val customAttributeName = "testAttribute"

        val result = withSpan("callChat") {
            it.setAttribute(customAttributeName, "testValue")

            callChat(client)
        }

        analyzeSpans().let { spans ->
            assertTrue("Two spans are created") { spans.size == 2 }

            val spanMap = spans.groupBy { it.name }.mapValues { it.value.first() }
            val outerSpan = spanMap["callChat"]
            val llmSpan = spanMap["OpenAI-generation"]

            assertNotNull(outerSpan)
            assertNotNull(llmSpan)

            assertEquals(llmSpan.parentSpanId, outerSpan.spanId, "LLM span is a child of the outer span")
            assertEquals(
                result.toString(),
                outerSpan.attributes.asMap()[AttributeKey.stringKey("output")],
                "Outputs is properly captured"
            )
            assertNotNull(outerSpan.attributes.asMap()[AttributeKey.stringKey(customAttributeName)])
        }
    }

    @Test
    fun testWithSpanTracingDisabled() {
        TracingManager.isTracingEnabled = false

        val client = createOpenAIClient().apply { instrument(this) }

        val customAttributeName = "testAttribute"

        withSpan("callChat") {
            it.setAttribute(customAttributeName, "testValue")
            callChat(client)
        }

        analyzeSpans().let { spans ->
            assertTrue("No spans are created") { spans.isEmpty() }
        }
    }
}

private fun callChat(
    client: OpenAIClient,
    model: String = ChatModel.GPT_4O_MINI.asString(),
    systemMessage: String = "You are a helpful assistant!",
    userMessage: String = "Tell me what model are you!",
    temperature: Double = 1.0
): ChatCompletion {
    val params = ChatCompletionCreateParams.builder()
        .addSystemMessage(systemMessage)
        .addUserMessage(userMessage)
        .model(model)
        .temperature(temperature)
        .build()

    return client.chat().completions().create(params)
}

private fun chatCallingFunction(client: OpenAIClient): String {
    val tracer = TracingManager.tracer

    val span = tracer.spanBuilder("custom call").startSpan()
    val scope = span.makeCurrent()

    try {
        val result = callChat(client)
        return result.choices().first().message().content().get()
    } catch (e: Exception) {
        span.recordException(e)
        span.setStatus(StatusCode.ERROR, "Chat call failed")
        throw e
    } finally {
        scope.close()
        span.end()
    }
}