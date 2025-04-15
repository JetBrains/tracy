package ai.dev.kit.eval.base

import ai.dev.kit.core.fluent.processor.TracingMetadataConfigurator
import ai.dev.kit.eval.base.dataclasses.TracesResponse
import com.openai.models.ChatModel
import com.openai.models.chat.completions.ChatCompletionCreateParams
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.reflect.KSuspendFunction1
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

open class TestDumbAutologTracingBase(
    private val configurator: TracingMetadataConfigurator,
    val getTraces: KSuspendFunction1<List<String>, TracesResponse>,
    private val client: KotlinLoggingClient
) {
    @Test
    fun testOpenAIAutoTracing() {
        client.withRun(client.currentExperimentId).use {
            val client = createOpenAIClient(
                dumbTraceMode = true,
                tracingMetadataConfigurator = configurator
            )
            val params = ChatCompletionCreateParams.Companion.builder()
                .addUserMessage("Generate polite greeting and introduce yourself")
                .model(ChatModel.Companion.GPT_4O_MINI).temperature(1.1).build()
            client.chat().completions().create(params)
        }

        val tracesResponse = runBlocking {
            getTraces(listOf(client.currentExperimentId))
        }

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
