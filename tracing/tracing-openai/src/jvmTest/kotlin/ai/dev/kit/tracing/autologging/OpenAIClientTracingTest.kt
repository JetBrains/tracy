package ai.dev.kit.tracing.autologging

import ai.dev.kit.clients.instrument
import ai.dev.kit.tracing.BaseOpenTelemetryTracingTest
import ai.dev.kit.tracing.LITELLM_URL
import ai.dev.kit.tracing.TracingManager
import ai.dev.kit.tracing.fluent.processor.withSpan
import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.models.chat.completions.ChatCompletion
import com.openai.models.chat.completions.ChatCompletionCreateParams
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_MODEL
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_SYSTEM
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Tag("SkipForNonLocal")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TracingTest : BaseOpenTelemetryTracingTest() {
    internal lateinit var client: OpenAIClient

    @BeforeAll
    fun createClient() {
        client = instrument(createLiteLLMClient())
    }

    @AfterAll
    fun closeClient() {
        client.close()
    }

    @Test
    fun testChat() {
        val model = "openai/gpt-4o-mini"
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
            assertEquals("https://litellm.labs.jb.gg", attributes[AttributeKey.stringKey("gen_ai.api_base")])
            assertEquals("system", attributes[AttributeKey.stringKey("gen_ai.prompt.0.role")])
            assertEquals("\"$systemMessage\"", attributes[AttributeKey.stringKey("gen_ai.prompt.0.content")])
            assertEquals("user", attributes[AttributeKey.stringKey("gen_ai.prompt.1.role")])
            assertEquals("\"$userMessage\"", attributes[AttributeKey.stringKey("gen_ai.prompt.1.content")])
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
        val customAttributeName = "testAttribute"

        val result = withSpan("callChat", emptyMap()) {
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
}

private fun callChat(
    client: OpenAIClient,
    model: String = "openai/gpt-4o-mini",
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

fun createLiteLLMClient(): OpenAIClient {
    return OpenAIOkHttpClient.builder()
        .baseUrl(LITELLM_URL)
        .apiKey(System.getenv("LITELLM_API_KEY") ?: error("LITELLM_API_KEY environment variable is not set"))
        .timeout(Duration.ofSeconds(60))
        .build()
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