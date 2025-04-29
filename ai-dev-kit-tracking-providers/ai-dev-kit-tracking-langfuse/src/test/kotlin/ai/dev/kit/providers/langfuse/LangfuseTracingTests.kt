package ai.dev.kit.providers.langfuse

import ai.dev.kit.core.fluent.processor.TracingFlowProcessor
import ai.dev.kit.providers.langfuse.KotlinLangfuseClient.TEST_PROJECT_NAME
import ai.dev.kit.providers.langfuse.KotlinLangfuseClient.currentExperimentId
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach

interface LangfuseTracingTests { // TODO: Implement methods
    companion object {
        @BeforeAll
        @JvmStatic
        fun setupProcessor() {
            TracingFlowProcessor.setupTracing(LangfuseDiContainer.di)

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
//        runBlocking { deleteAllTracesFromProject(TEST_PROJECT_NAME) }
    }
}
