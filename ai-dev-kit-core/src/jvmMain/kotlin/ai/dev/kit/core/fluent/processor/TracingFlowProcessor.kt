package ai.dev.kit.core.fluent.processor

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import org.kodein.di.DI

object TracingFlowProcessor {
    lateinit var di: DI
    val tracer: Tracer by lazy {
        GlobalOpenTelemetry.getTracer("ai.mlflow.evaluation.tracing")
    }

    fun setupTracing(di: DI) {
        this.di = di
        val tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(RootSpanExporter()))
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