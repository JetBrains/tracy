package ai.dev.kit.providers.mlflow.tracing

import ai.dev.kit.providers.mlflow.KotlinMlflowClient
import ai.dev.kit.providers.mlflow.MlflowContainerTests
import ai.dev.kit.providers.mlflow.fluent.setupMlflowTracing
import ai.dev.kit.tracing.fluent.processor.TracingFlowProcessor
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import kotlin.random.Random

interface MlflowTracingTests: MlflowContainerTests {
    companion object {
        @BeforeAll
        @JvmStatic
        fun setupProcessor() {
            setupMlflowTracing()
        }

        @AfterAll
        @JvmStatic
        fun removeTracing() {
            TracingFlowProcessor.teardownTracing()
        }
    }

    @BeforeEach
    fun setup() {
        KotlinMlflowClient.createIfDoesntExistReturnExperimentId(generateRandomString())
            ?: error("Failed to create experiment")
    }

    private fun generateRandomString(length: Int = 10): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..length).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }
}
