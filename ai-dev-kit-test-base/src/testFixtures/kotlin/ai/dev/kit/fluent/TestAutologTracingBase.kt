package ai.dev.kit.fluent

import ai.dev.kit.createOpenAIClient
import ai.dev.kit.tracing.fluent.KotlinLoggingClient
import ai.dev.kit.tracing.fluent.dataclasses.TracesResponse
import ai.dev.kit.tracing.fluent.processor.TracingFlowProcessor
import com.openai.models.ChatModel
import com.openai.models.chat.completions.ChatCompletionCreateParams
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.reflect.KSuspendFunction1
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

open class TestAutologTracingBase(
    val getTraces: KSuspendFunction1<String, TracesResponse>,
    private val client: KotlinLoggingClient
) {
    @Test
    fun testOpenAIAutoTracing() = runTest {
        client.withRun(client.currentExperimentId).use {
            val client = createOpenAIClient()
            val params = ChatCompletionCreateParams.Companion.builder()
                .addUserMessage("Generate polite greeting and introduce yourself")
                .model(ChatModel.Companion.GPT_4O_MINI).temperature(1.1).build()
            client.chat().completions().create(params)
        }

        TracingFlowProcessor.flushTraces()
        val tracesResponse = getTraces(client.currentExperimentId)

        assertEquals(1, tracesResponse.traces.size)
        val chatTrace = tracesResponse.traces.first()
        val traceInput = chatTrace.tags.firstOrNull { it.key == "traceSpans" }?.value
        assertNotNull(traceInput)
        val jsonInput = (Json.parseToJsonElement(traceInput) as? JsonArray)?.firstOrNull() as? JsonObject
        assertNotNull(jsonInput)
        assertEquals("CHAT_MODEL", (jsonInput["type"] as? JsonPrimitive)?.content)
        assertEquals(
            "{\"messages\":[{\"content\":\"Generate polite greeting and introduce yourself\",\"role\":\"user\"}],\"model\":\"gpt-4o-mini\",\"temperature\":1.1}",
            (jsonInput["inputs"] as? JsonPrimitive)?.content
        )
    }
}
