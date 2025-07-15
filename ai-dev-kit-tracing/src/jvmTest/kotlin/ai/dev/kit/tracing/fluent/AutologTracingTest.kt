package ai.dev.kit.tracing.fluent

import ai.dev.kit.instrument
import ai.dev.kit.tracing.BaseOpenTelemetryTracingTest
import ai.dev.kit.tracing.autologging.createLiteLLMClient
import com.google.genai.types.ListModelsConfig
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
import com.google.genai.Client as GeminiClient
import com.google.genai.types.GenerateContentConfig as GeminiGenerateContentConfig
import com.google.genai.types.HttpOptions as GeminiHttpOptions

const val LITELLM_URL = "https://litellm.labs.jb.gg"

@Tag("SkipForNonLocal")
class AutologTracingTest() : BaseOpenTelemetryTracingTest() {
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
            trace.attributes[AttributeKey.stringKey("gen_ai.openai.api_base")]
        )
        assertTrue(
            trace.attributes[AttributeKey.stringKey("gen_ai.response.model")]?.startsWith(ChatModel.Companion.GPT_4O_MINI.asString()) ?: false
        )
        val content = trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.content")]
        assertNotNull(content)
        assertTrue(content.isNotEmpty())
    }

    @Test
    fun `test Gemini auto tracing`() = runTest {
        val client = instrument(createGeminiClient())

        client.models.generateContent(
            "gemini-1.5-flash",
            "Generate polite greeting and introduce yourself",
            GeminiGenerateContentConfig.builder()
                .temperature(0.8f)
                .build()
        )

        // view available models
        /*
        val result = client.models.list(ListModelsConfig.builder().build()).toList()
        for (m in result) {
            println("RESULT: ${m.name()}")
        }
        */

        val traces = analyzeSpans()

        /*assertEquals(1, traces.size)
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
        assertTrue(content.isNotEmpty())*/
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

    private fun createGeminiClient(): GeminiClient {
        return GeminiClient.builder()
            .apiKey(System.getenv("LITELLM_API_KEY") ?: error("LITELLM_API_KEY environment variable is not set"))
            .httpOptions(
                GeminiHttpOptions.builder()
                    .baseUrl("$LITELLM_URL/gemini")
                    .timeout(Duration.ofSeconds(60).toMillis().toInt())
                    .build()
            )
            .build()
    }
}
