package ai.mlflow.tracing

import com.openai.models.ChatCompletionCreateParams
import com.openai.models.ChatModel
import org.example.ai.createOpenAIClient
import org.example.ai.mlflow.KotlinMlflowClient
import org.example.ai.mlflow.fluent.processor.TracingFlowProcessor
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.random.Random

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
//        KotlinMlflowClient.deleteExperiment(KotlinMlflowClient.currentExperimentId)
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
            val completions = client.chat().completions().create(params)

            println(completions.choices().first().message().content().get())
        }
    }

    fun generateRandomString(length: Int = 10): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..length).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }
}
