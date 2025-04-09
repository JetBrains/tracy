package ai.core.fluent.processor

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor


object TracingFlowProcessor {
    val tracer: Tracer by lazy {
        GlobalOpenTelemetry.getTracer("ai.mlflow.evaluation.tracing")
    }

    fun setupTracing(tracePublisher: TracePublisher) {
        val tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(RootSpanExporter(tracePublisher)))
            .build()

        GlobalOpenTelemetry.set(
            OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build()
        )
    }

    fun teardownTracing() {
        GlobalOpenTelemetry.resetForTest()
    }
}
