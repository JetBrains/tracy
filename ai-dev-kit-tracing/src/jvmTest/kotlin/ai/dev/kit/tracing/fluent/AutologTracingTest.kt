package ai.dev.kit.tracing.fluent

import ai.dev.kit.example.createGrazieExecutor
import ai.dev.kit.instrument
import ai.jetbrains.code.prompt.llm.JetBrainsAIModels
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.message.Message
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

const val LITELLM_URL = "https://litellm.labs.jb.gg"

@KotlinFlowTrace
suspend fun grazieCall(): List<Message.Response> {
    return createGrazieExecutor().execute(
        prompt("base-prompt") {
            system("You are a helpful assistant.")
            user("Hi!")
        },
        model = JetBrainsAIModels.Anthropic_Sonnet_3_7,
        tools = emptyList()
    )
}

@Tag("SkipForNonLocal")
class AutologTracingTest() : BaseTracingTest() {
    @Test
    fun `Grazie Executor test`() = runTest {
        val experimentId = createExperimentId()
        withProjectId(experimentId) {
            grazieCall()
        }

        val traces = getTraces(experimentId)

        assertEquals(2, traces.size)
        val trace = traces.firstOrNull()
        assertNotNull(trace)
    }

    @Test
    fun `test OpenAI auto tracing`() = runTest {
        val experimentId = createExperimentId()
        withProjectId(experimentId) {
            val client = instrument(createLiteLLMClient())
            val params = ChatCompletionCreateParams.Companion.builder()
                .addUserMessage("Generate polite greeting and introduce yourself")
                .model(ChatModel.Companion.GPT_4O_MINI).temperature(1.1).build()
            client.chat().completions().create(params)
        }

        val traces = getTraces(experimentId)

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
    fun `test OpenAI auto tracing when instrumentation is off`() = runTest {
        val experimentId = createExperimentId()
        val client = createLiteLLMClient()
        val params = ChatCompletionCreateParams.Companion.builder()
            .addUserMessage("Generate polite greeting and introduce yourself")
            .model(ChatModel.Companion.GPT_4O_MINI).temperature(1.1).build()
        val result = client.chat().completions().create(params)

        val traces = getTraces(experimentId)

        assertEquals(0, traces.size)
        assertTrue(
            result.model().startsWith(ChatModel.Companion.GPT_4O_MINI.asString())
        )
        val content = result.choices().first().message().content().getOrNull()
        assertNotNull(content)
        assertTrue(content.isNotEmpty())
    }

    private fun createLiteLLMClient(): OpenAIClient {
        return OpenAIOkHttpClient.builder()
            .baseUrl(LITELLM_URL)
            .apiKey(System.getenv("LITELLM_API_KEY") ?: error("LITELLM_API_KEY environment variable is not set"))
            .timeout(Duration.ofSeconds(60))
            .build()
    }
}
