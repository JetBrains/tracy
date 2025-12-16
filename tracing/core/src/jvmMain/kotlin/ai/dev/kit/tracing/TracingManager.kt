package ai.dev.kit.tracing

import ai.dev.kit.config.BuildConfig
import ai.dev.kit.exporters.BaseExporterConfig
import ai.dev.kit.tracing.TracingManager.setSdk
import ai.dev.kit.tracing.policy.ContentCapturePolicy
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

    private val logger = KotlinLogging.logger {}

    private val hasLoggedMissingSdk = AtomicBoolean(false)

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

    /**
     * Policy that controls capturing inputs and outputs in spans.
     * Disabled by default. Can be overridden programmatically at runtime.
     *
     * @see ContentCapturePolicy
     */
    @Volatile
    var contentCapturePolicy = ContentCapturePolicy.fromEnvironment()
        private set

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
     * Enables the capture of sensitive content (inputs and outputs) for tracing spans.
     *
     * This method overrides the default behavior of sensitive content capture,
     * which is disabled according to OpenTelemetry (OTEL) guidance. By setting the
     * `contentCapturePolicy` to capture both inputs and outputs, it modifies the
     * tracing configuration to allow the collection of this information.
     *
     * Note: The `ContentCapturePolicy` class is used to define the capture policy.
     * Ensure that capturing sensitive content aligns with your organization's data
     * privacy and compliance standards before enabling this behavior.
     *
     * Equivalent to:
     * ```kotlin
     * TracingManager.withCapturingPolicy(
     *    ContentCapturePolicy(
     *       captureInputs = true,
     *       captureOutputs = true,
     *    )
     * )
     * ```
     *
     * @see withCapturingPolicy
     */
    fun traceSensitiveContent() {
        contentCapturePolicy = ContentCapturePolicy(
            captureInputs = true,
            captureOutputs = true,
        )
    }

    /**
     * Sets the capturing policy for handling sensitive content within spans.
     *
     * @see [traceSensitiveContent]
     *
     * @param policy the content capture policy that dictates whether inputs and outputs
     *               containing sensitive data should be captured. By default, sensitive
     *               content is not captured in accordance with OpenTelemetry guidelines.
     */
    fun withCapturingPolicy(policy: ContentCapturePolicy) {
        contentCapturePolicy = policy
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