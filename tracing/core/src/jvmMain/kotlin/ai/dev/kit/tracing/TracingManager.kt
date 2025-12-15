package ai.dev.kit.tracing

import ai.dev.kit.config.BuildConfig
import ai.dev.kit.exporters.BaseExporterConfig
import ai.dev.kit.tracing.TracingManager.setSdk
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.OpenTelemetrySdk
import mu.KotlinLogging
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

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

    /**
     * Indicates whether tracing is enabled at runtime.
     *
     * The initial value is derived from the `IS_TRACY_ENABLED` environment variable. If the variable is
     * not set, tracing is disabled by default. This property can be changed programmatically at any time
     * to enable or disable tracing dynamically.
     */
    @Volatile
    var isTracingEnabled: Boolean = System.getenv("IS_TRACY_ENABLED")?.toBoolean() ?: false

    @Volatile
    internal var openTelemetrySdk: OpenTelemetrySdk? = null

    private val logger = KotlinLogging.logger {}

    private val hasLoggedMissingSdk = AtomicBoolean(false)

    /**
     * Provides the default [Tracer] instance for the AI Development Kit.
     *
     * Behavior:
     * - If tracing is enabled and an OpenTelemetry SDK has been initialized via [setSdk], returns a working tracer.
     * - Otherwise returns a no‑op tracer: calls on it will create no real spans.
     */
    val tracer: Tracer
        get() {
            if (!isTracingEnabled) {
                return NOOP_TRACER
            }
            openTelemetrySdk?.let { sdk ->
                return sdk.getTracer(AI_DEVELOPMENT_KIT_TRACER, BuildConfig.VERSION)
            }
            // First access when SDK missing and tracing enabled: warn, then use no‑op
            if (hasLoggedMissingSdk.compareAndSet(false, true)) {
                logger.warn {
                    "Tracing is enabled but OpenTelemetry SDK is not set. Returning a no-op tracer."
                }
            }
            return NOOP_TRACER
        }

    private val NOOP_TRACER: Tracer = OpenTelemetry.noop().getTracer(AI_DEVELOPMENT_KIT_TRACER)


    /**
     * Sets the [OpenTelemetrySdk].
     *
     * @param sdk the OpenTelemetry SDK instance to use.
     */
    fun setSdk(sdk: OpenTelemetrySdk) {
        openTelemetrySdk = sdk
        hasLoggedMissingSdk.set(false)
    }

    /**
     * Forces flushing of any pending spans to their respective exporters.
     *
     * @param timeoutSeconds maximum time to wait for flushing, in seconds.
     * @return true if flush was successful, false otherwise.
     */
    fun flushTraces(timeoutSeconds: Long = 5) =
        openTelemetrySdk?.sdkTracerProvider?.forceFlush()?.join(timeoutSeconds, TimeUnit.SECONDS)


    /**
     * Shuts down the OpenTelemetry SDK.
     *
     * @param timeoutSeconds maximum time to wait for shutdown, in seconds.
     * @return true if shutdown was successful, false otherwise.
     */
    fun shutdownTracing(timeoutSeconds: Long = 5) =
        openTelemetrySdk?.sdkTracerProvider?.shutdown()?.join(timeoutSeconds, TimeUnit.SECONDS)
}