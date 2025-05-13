package ai.dev.kit.tracing.fluent.processor

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.kodein.di.DI

object TracingFlowProcessor {
    // Coroutine scope dedicated to managing and sending traces to the provider asynchronously
    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    lateinit var di: DI
    val tracer: Tracer by lazy {
        GlobalOpenTelemetry.getTracer("ai.mlflow.evaluation.tracing")
    }
    private val spanExporter: RootSpanExporter by lazy {
        RootSpanExporter(scope)
    }

    fun setupTracing(di: DI) {
        this.di = di
        val tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
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

    fun flushTraces() { spanExporter.flush() }
    fun shutdownTraces() { spanExporter.shutdown() }
}
