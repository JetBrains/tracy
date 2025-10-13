package ai.dev.kit.tracing

import io.opentelemetry.sdk.OpenTelemetrySdk
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.TimeUnit

object TracingManager {
    const val AI_DEVELOPMENT_KIT_TRACER = "ai-development-kit"
    private lateinit var openTelemetrySdk: OpenTelemetrySdk
    val tracer
        get() = openTelemetrySdk.getTracer(AI_DEVELOPMENT_KIT_TRACER)

    fun setup(tracingConfig: TracingConfig) {
        openTelemetrySdk = setupTracing(tracingConfig)
    }

    fun flushTraces(timeoutSeconds: Long = 1) = openTelemetrySdk
        .sdkTracerProvider
        .forceFlush()
        .join(timeoutSeconds, TimeUnit.SECONDS)
        .isSuccess


    fun shutdownTracing(timeoutSeconds: Long = 5) = openTelemetrySdk
        .sdkTracerProvider
        .shutdown()
        .join(timeoutSeconds, TimeUnit.SECONDS)
        .isSuccess

    @TestOnly
    fun setSdkForTest(testOpenTelemetrySdk: OpenTelemetrySdk) {
        openTelemetrySdk = testOpenTelemetrySdk
    }
}