package ai.dev.kit.eval.mlflow.tracing

import ai.dev.kit.core.fluent.processor.TracingFlowProcessor
import ai.dev.kit.eval.mlflow.KotlinMlflowClient
import ai.dev.kit.eval.mlflow.MlflowContainerTests
import ai.dev.kit.eval.mlflow.fluent.MlflowTracePublisher
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
            TracingFlowProcessor.setupTracing(MlflowTracePublisher)
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
