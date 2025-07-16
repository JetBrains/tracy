package ai.dev.kit.tracing

import io.opentelemetry.sdk.trace.SdkTracerProvider
import java.util.concurrent.TimeUnit

object TracingManager {
    private lateinit var tracerProvider: SdkTracerProvider

    fun setup(tracingConfig: TracingConfig) {
        tracerProvider = setupTracing(tracingConfig)
    }

    fun flushTraces(timeoutSeconds: Long = 1) = tracerProvider
        .forceFlush()
        .join(timeoutSeconds, TimeUnit.SECONDS)


    fun shutdownTracing(timeoutSeconds: Long = 5) = tracerProvider
        .shutdown()
        .join(timeoutSeconds, TimeUnit.SECONDS)
}