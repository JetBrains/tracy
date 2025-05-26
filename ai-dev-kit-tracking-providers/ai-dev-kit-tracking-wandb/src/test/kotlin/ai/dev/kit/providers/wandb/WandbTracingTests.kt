package ai.dev.kit.providers.wandb

import ai.dev.kit.providers.wandb.fluent.setupWandbTracing
import ai.dev.kit.tracing.fluent.processor.TracingFlowProcessor
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll

private const val TEST_PROJECT_NAME = "ai-dev-kit-tracing-tests"

interface WandbTracingTests {
    companion object {
        @BeforeAll
        @JvmStatic
        fun setupProcessor() {
            setupWandbTracing()
            runBlocking { deleteAllTracesFromProject(TEST_PROJECT_NAME) }
        }

        @AfterAll
        @JvmStatic
        fun removeTracing() {
            TracingFlowProcessor.teardownTracing()
        }

        fun getExperimentId(): String {
            TODO("How do I get experiment ID for W&B?")
        }
    }

    @AfterEach
    fun cleaning() {
        runBlocking { deleteAllTracesFromProject(TEST_PROJECT_NAME) }
    }
}
