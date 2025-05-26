package ai.dev.kit.providers.mlflow.tracing

import ai.dev.kit.providers.mlflow.KotlinMlflowClient
import ai.dev.kit.providers.mlflow.MlflowEvaluationClient
import ai.dev.kit.providers.mlflow.fluent.setupMlflowTracing
import ai.dev.kit.tracing.fluent.processor.TracingFlowProcessor
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll

interface MlflowTracingTests {
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

        @AfterAll
        @JvmStatic
        fun cleanExperimentIds() {
            experimentIds.forEach {
                KotlinMlflowClient.deleteExperiment(it)
            }
        }

        private val experimentIds = mutableSetOf<String>()
        fun getExperimentId(): String = runBlocking {
            val id = MlflowEvaluationClient.getOrCreateExperiment(experimentName = generateRandomString())
            experimentIds.add(id)
            return@runBlocking id
        }
    }
}

private fun generateRandomString(length: Int = 10): String {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    return (1..length).map { chars.random() }.joinToString("")
}
