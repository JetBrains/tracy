package ai.dev.kit.tracing

import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.OpenTelemetrySdk
import java.util.concurrent.TimeUnit

object TracingManager {
    /*
     * Default name of the tracer.
     */
    const val AI_DEVELOPMENT_KIT_TRACER = "ai-development-kit"

    @Volatile
    internal var openTelemetrySdk: OpenTelemetrySdk? = null

    val tracer: Tracer
        get() = requireNotNull(openTelemetrySdk) {
            "TracingManager not initialized. Call setup(tracingConfig) or setSdk(...) first to initialize the OpenTelemetry SDK."
        }.getTracer(AI_DEVELOPMENT_KIT_TRACER)

    fun setSdk(sdk: OpenTelemetrySdk) {
        openTelemetrySdk = sdk
    }

    fun flushTraces(timeoutSeconds: Long = 5) =
        openTelemetrySdk?.sdkTracerProvider?.forceFlush()?.join(timeoutSeconds, TimeUnit.SECONDS)?.isSuccess ?: false

    fun shutdownTracing(timeoutSeconds: Long = 5) =
        openTelemetrySdk?.sdkTracerProvider?.shutdown()?.join(timeoutSeconds, TimeUnit.SECONDS)?.isSuccess ?: false
}