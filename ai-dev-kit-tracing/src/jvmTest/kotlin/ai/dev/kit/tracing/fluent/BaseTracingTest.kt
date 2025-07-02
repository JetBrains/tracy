package ai.dev.kit.tracing.fluent

import ai.dev.kit.tracing.fluent.handlers.PlatformMethod
import ai.dev.kit.tracing.fluent.processor.*
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseTracingTest {
    private val inMemorySpanExporter: InMemorySpanExporter = InMemorySpanExporter.create()

    @BeforeAll
    fun setupTracingProcessor() {
        TracingFlowProcessor.setupTracing(
            tracingMetadataConfigurator = createSimpleTracingMetadataConfigurator(),
            tracePublisher = createSimpleTracePublisher(),
            spanExporter = inMemorySpanExporter
        )
    }

    @AfterAll
    fun removeTracingProcessor() {
        TracingFlowProcessor.teardownTracing()
    }

    @AfterEach
    fun resetInMemoryExporter() {
        inMemorySpanExporter.reset()
    }

    fun createExperimentId(): String = TESTING_EXPERIMENT_ID

    fun getTraces(experimentId: String): List<SpanData> {
        // Note: With the InMemory exporter, flushing is not strictly needed because it does not batch spans.
        // However, in tests, getTraces MUST always flush traces before exporting them to ensure consistency!
        inMemorySpanExporter.flush()
        return inMemorySpanExporter.finishedSpanItems
    }

    private fun createSimpleTracingMetadataConfigurator(): TracingMetadataConfigurator =
        object : TracingMetadataConfigurator() {
            override fun createTraceInfo(
                spanBuilder: SpanBuilder, method: PlatformMethod, spanName: String
            ): Span {
                return spanBuilder.startSpan()
            }
        }

    private fun createSimpleTracePublisher(): TracePublisher = object : TracePublisher {
        override suspend fun publishTrace(trace: List<SpanData>) {}
    }

    companion object {
        const val TESTING_EXPERIMENT_ID = "Testing experiment id"
    }
}
