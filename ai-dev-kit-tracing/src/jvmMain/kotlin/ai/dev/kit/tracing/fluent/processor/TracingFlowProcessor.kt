package ai.dev.kit.tracing.fluent.processor

import ai.dev.kit.tracing.AI_DEVELOPMENT_KIT_TRACER
import ai.dev.kit.tracing.fluent.FluentSpanAttributes
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

object TracingFlowProcessor {
    // Define the coroutine scope for managing asynchronous trace publishing
    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    // Configuration and publisher for tracing
    lateinit var tracingMetadataConfigurator: TracingMetadataConfigurator
    lateinit var tracePublisher: TracePublisher
    lateinit var  tracer: Tracer
    // Span exporter, initialized during setup
    private lateinit var spanExporter: SpanExporter

    fun setupTracing(
        tracingMetadataConfigurator: TracingMetadataConfigurator,
        tracePublisher: TracePublisher,
        spanExporter: SpanExporter = RootSpanExporter(scope)
    ) {
        this.tracingMetadataConfigurator = tracingMetadataConfigurator
        this.tracePublisher = tracePublisher
        this.spanExporter = spanExporter

        val tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
            .build()

        OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .buildAndRegisterGlobal()

        this.tracer = GlobalOpenTelemetry.getTracer(AI_DEVELOPMENT_KIT_TRACER)
    }

    fun teardownTracing() {
        GlobalOpenTelemetry.resetForTest()
    }

    fun flushTraces() { spanExporter.flush() }
    fun shutdownTraces() { spanExporter.shutdown() }

    fun addTagsToCurrentTrace(tags: List<String>) {
        Span.fromContext(getOpenTelemetryContext(scope.coroutineContext)).setAttribute(FluentSpanAttributes.TRACE_TAGS.key, tags.toString())
    }
}
