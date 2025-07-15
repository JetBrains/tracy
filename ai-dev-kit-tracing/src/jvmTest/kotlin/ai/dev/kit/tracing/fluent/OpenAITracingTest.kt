package ai.dev.kit.tracing.fluent

import ai.dev.kit.instrument
import ai.dev.kit.tracing.BaseOpenTelemetryTracingTest
import ai.dev.kit.tracing.LITELLM_URL
import ai.dev.kit.tracing.autologging.createLiteLLMClient
import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.Model
import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.models.ChatModel
import com.openai.models.chat.completions.ChatCompletionCreateParams
import io.opentelemetry.api.common.AttributeKey
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.jvm.optionals.getOrNull
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


@Tag("SkipForNonLocal")
class OpenAITracingTest() : BaseOpenTelemetryTracingTest() {
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
            trace.attributes[AttributeKey.stringKey("gen_ai.response.model")]?.startsWith(ChatModel.Companion.GPT_4O_MINI.asString()) ?: false
        )
        val content = trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.content")]
        assertNotNull(content)
        assertTrue(content.isNotEmpty())
    }

    @Test
    fun `test Anthropic auto tracing`() = runTest {
        val client = createAnthropicClient()

        val params = MessageCreateParams.builder()
            .maxTokens(1000L)
            .temperature(0.8)
            .addUserMessage("Generate polite greeting and introduce yourself")
            .addAssistantMessage("Hi! I'm Claude")
            .addUserMessage("What is your name?")
            .model(Model.CLAUDE_3_7_SONNET_LATEST)
            .build()

        val message = client.messages().create(params)
        client.completions()
        println("MESSAGE: $message")

        /*
        val params = com.anthropic.models.completions.CompletionCreateParams.builder()
            .prompt("Generate polite greeting and introduce yourself")
            .model(Model.CLAUDE_3_5_SONNET_20240620).temperature(1.1)
            .maxTokensToSample(1000L)
            .build()
        client.completions().create(params)
        */

        val traces = analyzeSpans()
        println("TRACES: $traces")

        /*
        assertEquals(1, traces.size)
        val trace = traces.firstOrNull()
        assertNotNull(trace)

        assertEquals(
            LITELLM_URL,
            trace.attributes[AttributeKey.stringKey("gen_ai.openai.api_base")]
        )
        assertTrue(
            trace.attributes[AttributeKey.stringKey("gen_ai.response.model")]?.startsWith(ChatModel.Companion.GPT_4O_MINI.asString()) ?: false
        )
        val content = trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.content")]
        assertNotNull(content)
        assertTrue(content.isNotEmpty())
        */
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

    private fun createAnthropicClient(): AnthropicClient {
        return AnthropicOkHttpClient.builder()
            .baseUrl(LITELLM_URL)
            .apiKey(System.getenv("LITELLM_API_KEY") ?: error("LITELLM_API_KEY environment variable is not set"))
            .timeout(Duration.ofSeconds(60))
            .build()
    }
}
