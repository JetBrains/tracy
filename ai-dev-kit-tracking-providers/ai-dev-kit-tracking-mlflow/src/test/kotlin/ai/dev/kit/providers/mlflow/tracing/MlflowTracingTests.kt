package ai.dev.kit.providers.mlflow.tracing

import ai.dev.kit.core.fluent.processor.TracingFlowProcessor
import ai.dev.kit.providers.mlflow.KotlinMlflowClient
import ai.dev.kit.providers.mlflow.MlflowContainerTests
import ai.dev.kit.providers.mlflow.MlflowDiContainer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import kotlin.random.Random

open class MlflowTracingTests: MlflowContainerTests() {
    companion object {
        @BeforeAll
        @JvmStatic
        fun setupProcessor() {
            TracingFlowProcessor.setupTracing(MlflowDiContainer.di)
        }

        @AfterAll
        @JvmStatic
        fun removeTracing() {
            TracingFlowProcessor.teardownTracing()
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
