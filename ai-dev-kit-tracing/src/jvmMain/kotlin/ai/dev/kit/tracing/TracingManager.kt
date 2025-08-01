package ai.dev.kit.tracing

import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.trace.SdkTracerProvider
import java.util.concurrent.TimeUnit

object TracingManager {
    lateinit var sdk: OpenTelemetrySdk
    private val tracerProvider: SdkTracerProvider
        get() = sdk.tracerProvider as SdkTracerProvider

    fun setup(tracingConfig: TracingConfig) {
        sdk = setupTracing(tracingConfig)
    }

    fun flushTraces(timeoutSeconds: Long = 1) = tracerProvider
        .forceFlush()
        .join(timeoutSeconds, TimeUnit.SECONDS)


    fun shutdownTracing(timeoutSeconds: Long = 5) = tracerProvider
        .shutdown()
        .join(timeoutSeconds, TimeUnit.SECONDS)
}