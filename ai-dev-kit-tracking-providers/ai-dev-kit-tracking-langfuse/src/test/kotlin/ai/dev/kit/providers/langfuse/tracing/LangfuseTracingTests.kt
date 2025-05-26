package ai.dev.kit.providers.langfuse.tracing

import ai.dev.kit.providers.langfuse.fluent.setupLangfuseTracing
import ai.dev.kit.tracing.fluent.processor.TracingFlowProcessor
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll

interface LangfuseTracingTests { // TODO: Implement methods
    companion object {
        @BeforeAll
        @JvmStatic
        fun setupProcessor() {
            setupLangfuseTracing()
        }

        @AfterAll
        @JvmStatic
        fun removeTracing() {
            TracingFlowProcessor.teardownTracing()
        }
    }

    @AfterEach
    fun cleaning() {
//        runBlocking { deleteAllTracesFromProject(TEST_PROJECT_NAME) }
    }
}
