package ai.dev.kit.providers.mlflow.tracing

import ai.dev.kit.core.eval.createOpenAIClient
import com.openai.models.ChatModel
import com.openai.models.chat.completions.ChatCompletionCreateParams
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import ai.dev.kit.providers.mlflow.KotlinMlflowClient
import ai.dev.kit.providers.mlflow.fluent.MlflowTracingMetadataConfigurator
import ai.dev.kit.providers.mlflow.getTraces
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TestAutologTracing: MlflowTracingTests() {
    @Test
    fun testOpenAIAutoTracing() {
        KotlinMlflowClient.withRun(KotlinMlflowClient.currentExperimentId).use {
            val client = createOpenAIClient(tracingMetadataConfigurator = MlflowTracingMetadataConfigurator)
            val params = ChatCompletionCreateParams.Companion.builder()
                .addUserMessage("Generate polite greeting and introduce yourself")
                .model(ChatModel.Companion.GPT_4O_MINI)
                .temperature(1.1)
                .build()
            client.chat().completions().create(params)
        }

        val tracesResponse = runBlocking {
            getTraces(listOf(KotlinMlflowClient.currentExperimentId))
        }

        assertEquals(1, tracesResponse.traces.size)
        val chatTrace = tracesResponse.traces.first()
        val traceInput = chatTrace.tags.firstOrNull { it.key == "mlflow.traceSpans" }?.value
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
