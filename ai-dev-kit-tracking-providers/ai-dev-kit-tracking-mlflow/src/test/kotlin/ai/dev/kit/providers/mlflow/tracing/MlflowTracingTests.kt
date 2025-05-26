package ai.dev.kit.providers.mlflow.tracing

import ai.dev.kit.providers.mlflow.fluent.setupMlflowTracing
import ai.dev.kit.tracing.fluent.processor.TracingFlowProcessor
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
    }
}
