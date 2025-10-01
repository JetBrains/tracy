package ai.dev.kit.tracing

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import java.time.Duration
import kotlin.test.AfterTest

const val LITELLM_URL = "https://litellm.labs.jb.gg"

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseOpenTelemetryTracingTest {
    internal lateinit var spanExporter: InMemorySpanExporter

    @BeforeAll
    fun setupTelemetry() {
        val testTracing = initOpenTelemetry()
        TracingManager.setSdkForTest(testTracing.openTelemetrySdk)
        spanExporter = testTracing.spanExporter
    }

    @AfterTest
    fun cleanSpans() {
        spanExporter.reset()
    }

    @AfterAll
    fun shutdownTelemetry() {
        TracingManager.shutdownTracing()
    }

    fun analyzeSpans(): List<SpanData> {
        TracingManager.flushTraces(10)
        return spanExporter.finishedSpanItems.mapNotNull { it }
    }
}

private fun initOpenTelemetry(): TestTracing {
    val resource = Resource.getDefault()
        .merge(
            Resource.create(
                Attributes.of(AttributeKey.stringKey("service.name"), "ai-development-kit")
            )
        )

    val spanExporter = InMemorySpanExporter.create()
    val batchProcessor = BatchSpanProcessor.builder(spanExporter)
        .setScheduleDelay(Duration.ofMillis(100))
        .setMaxExportBatchSize(512)
        .setMaxQueueSize(2048)
        .build()


    val tracerProvider = SdkTracerProvider.builder()
        .setResource(resource)
        .addSpanProcessor(batchProcessor)
        .build()

    val openTelemetry = OpenTelemetrySdk.builder()
        .setTracerProvider(tracerProvider)
        .build()

    Runtime.getRuntime().addShutdownHook(Thread {
        openTelemetry.sdkTracerProvider.shutdown()
    })

    return TestTracing(openTelemetry, spanExporter)
}

private data class TestTracing(
    val openTelemetrySdk: OpenTelemetrySdk,
    val spanExporter: InMemorySpanExporter
)