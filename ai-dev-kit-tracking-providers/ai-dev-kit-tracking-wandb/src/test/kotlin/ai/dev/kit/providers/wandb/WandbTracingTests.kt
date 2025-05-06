package ai.dev.kit.providers.wandb

import ai.dev.kit.core.fluent.processor.TracingFlowProcessor
import ai.dev.kit.providers.wandb.KotlinWandbClient.TEST_PROJECT_NAME
import ai.dev.kit.providers.wandb.KotlinWandbClient.currentExperimentId
import ai.dev.kit.providers.wandb.fluent.setupWandbTracing
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach

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
    }

    @BeforeEach
    fun setup() {
        currentExperimentId = TEST_PROJECT_NAME
    }

    @AfterEach
    fun cleaning() {
        runBlocking { deleteAllTracesFromProject(TEST_PROJECT_NAME) }
    }
}
