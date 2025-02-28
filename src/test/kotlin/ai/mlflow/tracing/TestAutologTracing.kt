package ai.mlflow.tracing

import com.openai.models.ChatCompletionCreateParams
import com.openai.models.ChatModel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.example.ai.createOpenAIClient
import org.example.ai.mlflow.KotlinMlflowClient
import org.example.ai.mlflow.fluent.processor.TracingFlowProcessor
import org.example.ai.mlflow.getTraces
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TestAutologTracing {
    companion object {
        @BeforeAll
        @JvmStatic
        fun setupProcessor() {
            TracingFlowProcessor.setup()
        }
    }

    @BeforeEach
    fun setup() {
        KotlinMlflowClient.setExperimentByName(generateRandomString())
    }

    @AfterEach
    fun cleaning() {
        KotlinMlflowClient.deleteExperiment(KotlinMlflowClient.currentExperimentId)
    }

    @Test
    fun testOpenAIAutoTracing() {
        KotlinMlflowClient.withRun(KotlinMlflowClient.currentExperimentId).use {
            val client = createOpenAIClient()
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

    fun generateRandomString(length: Int = 10): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..length).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }
}
