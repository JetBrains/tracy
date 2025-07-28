package ai.dev.kit.tracing

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest

internal const val LITELLM_URL = "https://litellm.labs.jb.gg"

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseOpenTelemetryTracingTest {
    internal lateinit var tracerProvider: SdkTracerProvider
    internal lateinit var spanExporter: InMemorySpanExporter
    internal lateinit var tracer: Tracer

    companion object {
        protected const val LITELLM_URL = "https://litellm.labs.jb.gg"
    }

    @BeforeAll
    fun setupTelemetry() {
        val tracing = initOpenTelemetry()

        tracerProvider = tracing.tracerProvider
        spanExporter = tracing.spanExporter as InMemorySpanExporter
        tracer = GlobalOpenTelemetry.getTracer(AI_DEVELOPMENT_KIT_TRACER)
    }

    @AfterTest
    fun cleanSpans() {
        spanExporter.reset()
    }

    @AfterAll
    fun shutdownTelemetry() {
        tracerProvider.apply {
            forceFlush().join(1, TimeUnit.SECONDS)
            shutdown().join(1, TimeUnit.SECONDS)
        }
        GlobalOpenTelemetry.resetForTest()
    }

    fun analyzeSpans(): List<SpanData> {
        tracerProvider.forceFlush().join(30, TimeUnit.SECONDS)
        return spanExporter.finishedSpanItems.mapNotNull { it }
    }
}

fun initOpenTelemetry(): Tracing {
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
        .buildAndRegisterGlobal()

    val sdk = openTelemetry
    Runtime.getRuntime().addShutdownHook(Thread {
        sdk.sdkTracerProvider.shutdown()
    })

    return Tracing(tracerProvider, spanExporter)
}

data class Tracing(
    val tracerProvider: SdkTracerProvider,
    val spanExporter: SpanExporter
)