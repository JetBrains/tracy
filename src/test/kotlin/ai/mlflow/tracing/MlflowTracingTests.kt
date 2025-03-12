package ai.mlflow.tracing

import ai.MlflowContainerTests
import org.example.ai.mlflow.KotlinMlflowClient
import org.example.ai.mlflow.fluent.processor.TracingFlowProcessor
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import kotlin.random.Random

open class MlflowTracingTests: MlflowContainerTests() {
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

    private fun generateRandomString(length: Int = 10): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..length).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }
}