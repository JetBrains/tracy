package ai.dev.kit.tracing

import ai.dev.kit.exporters.BaseExporterConfig
import ai.dev.kit.tracing.TracingManager.setSdk
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.OpenTelemetrySdk
import java.util.concurrent.TimeUnit

/**
 * Manager for setting up and managing OpenTelemetry tracing for the AI Development Kit.
 *
 * This object provides utilities to:
 *  - Initialize and configure the OpenTelemetry SDK using a [BaseExporterConfig].
 *  - Obtain a default [Tracer] for the AI Development Kit, used in automatic tracing
 *    as well as annotation-based spans.
 *  - Flush and shut down traces gracefully.
 *
 * Usage example:
 * ```
 * TracingManager.setSdk(configureOpenTelemetrySdk(ConsoleExporterConfig()))
 * // ... your traced code ...
 * TracingManager.flushTraces()
 * ```
 *
 * @see BaseExporterConfig
 * @see io.opentelemetry.sdk.OpenTelemetrySdk
 */
object TracingManager {
    /*
     * Default name of the tracer.
     */
    private const val AI_DEVELOPMENT_KIT_TRACER = "ai-development-kit"

    @Volatile
    internal var openTelemetrySdk: OpenTelemetrySdk? = null

    /**
     * The default [Tracer] instance for the AI Development Kit.
     *
     * @throws IllegalStateException if the manager has not been initialized via [setSdk].
     */
    val tracer: Tracer
        get() = requireNotNull(openTelemetrySdk) {
            "TracingManager not initialized. Call setSdk(...) first to initialize the OpenTelemetry SDK."
        }.getTracer(AI_DEVELOPMENT_KIT_TRACER)

    /**
     * Sets the [OpenTelemetrySdk].
     *
     * @param sdk the OpenTelemetry SDK instance to use.
     */
    fun setSdk(sdk: OpenTelemetrySdk) {
        openTelemetrySdk = sdk
    }

    /**
     * Forces flushing of any pending spans to their respective exporters.
     *
     * @param timeoutSeconds maximum time to wait for flushing, in seconds.
     * @return true if flush was successful, false otherwise.
     */
    fun flushTraces(timeoutSeconds: Long = 5) =
        openTelemetrySdk?.sdkTracerProvider?.forceFlush()?.join(timeoutSeconds, TimeUnit.SECONDS)?.isSuccess ?: false


    /**
     * Shuts down the OpenTelemetry SDK.
     *
     * @param timeoutSeconds maximum time to wait for shutdown, in seconds.
     * @return true if shutdown was successful, false otherwise.
     */
    fun shutdownTracing(timeoutSeconds: Long = 5) =
        openTelemetrySdk?.sdkTracerProvider?.shutdown()?.join(timeoutSeconds, TimeUnit.SECONDS)?.isSuccess ?: false
}