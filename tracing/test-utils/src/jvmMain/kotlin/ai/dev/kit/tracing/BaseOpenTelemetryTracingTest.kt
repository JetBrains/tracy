package ai.dev.kit.tracing

import ai.dev.kit.tracing.policy.ContentCapturePolicy
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import java.time.Duration
import kotlin.test.AfterTest
import kotlin.test.BeforeTest


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseOpenTelemetryTracingTest {
    private lateinit var spanExporter: InMemorySpanExporter

    @BeforeAll
    fun setupTelemetry() {
        val testTracing = initOpenTelemetry()
        TracingManager.setSdk(testTracing.openTelemetrySdk)
        // Enable sensitive content capture for tests to keep existing assertions valid
        TracingManager.contentCapturePolicy = ContentCapturePolicy(
            captureInputs = true,
            captureOutputs = true,
        )
        spanExporter = testTracing.spanExporter
    }

    @BeforeTest
    fun enableTracingBeforeTest() {
        TracingManager.isTracingEnabled = true
    }

    @AfterTest
    fun resetExporter() = spanExporter.reset()

    @AfterAll
    fun shutdownTelemetry() {
        TracingManager.shutdownTracing()
    }

    protected fun analyzeSpans(): List<SpanData> {
        TracingManager.flushTraces(10)
        return spanExporter.finishedSpanItems.mapNotNull { it }
    }

    protected fun <T> flushTracesAndAssumeToolCalled(
        response: T,
        toolName: String,
        containsToolCall: (T, String) -> Boolean,
    ) {
        TracingManager.flushTraces(10)
        val called = containsToolCall(response, toolName)
        assumeTrue(called, "`$toolName` tool was not called")
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