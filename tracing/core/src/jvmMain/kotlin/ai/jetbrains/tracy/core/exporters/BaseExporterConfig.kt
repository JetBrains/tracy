package ai.jetbrains.tracy.core.exporters

import io.opentelemetry.exporter.logging.LoggingSpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder
import io.opentelemetry.sdk.trace.SpanLimits
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter
import java.util.concurrent.TimeUnit

/**
 * Base configuration class for OpenTelemetry tracing exporters.
 *
 * Provides common functionality for configuring tracing exporters, including
 *  - Creating [SpanExporter] instances for sending spans to external systems.
 *  - Configuring [SpanLimits] for spans, based on maximum attributes and attribute length.
 *  - Optionally logging spans to the console using a [LoggingSpanExporter] for local debugging.
 *
 * @property settings User-provided common settings controlling batching, console logging,
 *  shutdown behavior, and span attribute limits.
 *
 * @see [ExporterCommonSettings]
 * @see [SpanExporter]
 * @see [SpanLimits]
 */
abstract class BaseExporterConfig(
    val settings: ExporterCommonSettings
) {
    protected val resolvedMaxAttributes: Int = settings.maxNumberOfSpanAttributes
        ?: resolveEnvInt("MAX_NUMBER_OF_SPAN_ATTRIBUTES",
            ExporterCommonSettings.DEFAULT_NUMBER_OF_SPAN_ATTRIBUTES
        )
    protected val resolvedMaxAttributeLength: Int = settings.maxSpanAttributeValueLength
        ?: resolveEnvInt("MAX_SPAN_ATTRIBUTE_VALUE_LENGTH",
            ExporterCommonSettings.DEFAULT_SPAN_ATTRIBUTE_VALUE_LENGTH
        )

    init {
        require(resolvedMaxAttributes > 0) { "Max Number Of Span Attributes must be positive" }
        require(resolvedMaxAttributeLength > 0) { "Max Span Attribute Value Length must be positive" }
    }

    /**
     * Creates a [SpanExporter] instance based on the current exporter configuration.
     *
     * @return A configured [SpanExporter], or `null` if no exporter is applicable.
     */
    abstract fun createSpanExporter(): SpanExporter?

    /**
     * Configures the given [SdkTracerProviderBuilder] with the appropriate span processors
     * for this exporter configuration.
     *
     * - Adds a [BatchSpanProcessor] for the exporter returned by [createSpanExporter], if present.
     * - Adds a [SimpleSpanProcessor] logging spans to console if [ExporterCommonSettings.traceToConsole] is true.
     *
     * @param sdkTracerBuilder the [SdkTracerProviderBuilder] to configure
     */
    open fun configureSpanProcessors(sdkTracerBuilder: SdkTracerProviderBuilder) {
        createSpanExporter()?.let { exporter ->
            sdkTracerBuilder.addSpanProcessor(
                BatchSpanProcessor.builder(exporter)
                    .setScheduleDelay(settings.flushIntervalMs, TimeUnit.MILLISECONDS)
                    .setMaxExportBatchSize(settings.flushThreshold)
                    .build()
            )
        }
        if (settings.traceToConsole) {
            sdkTracerBuilder.addConsoleLoggingSpanProcessor()
        }
    }

    /**
     * Constructs a [SpanLimits] instance using the resolved configuration for
     * maximum attributes per span and maximum attribute value length.
     *
     * @return a [SpanLimits] instance suitable for [SdkTracerProviderBuilder.setSpanLimits].
     */
    open fun createSpanLimits(): SpanLimits {
        return SpanLimits.builder()
            .setMaxNumberOfAttributes(resolvedMaxAttributes)
            .setMaxAttributeValueLength(resolvedMaxAttributeLength)
            .build()
    }

    /**
     * Adds a [SimpleSpanProcessor] with a [LoggingSpanExporter] to this [SdkTracerProviderBuilder].
     *
     * The [LoggingSpanExporter] outputs every finished span at `INFO` level
     * using `java.util.logging`.
     */
    protected fun SdkTracerProviderBuilder.addConsoleLoggingSpanProcessor(): SdkTracerProviderBuilder {
        val spanExporter = LoggingSpanExporter.create()
        addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
        return this
    }

    companion object {
        internal fun resolveEnvInt(envKey: String, default: Int): Int =
            System.getenv(envKey)?.toInt() ?: default

        internal fun resolveRequiredEnvVar(argValue: String?, envKey: String): String {
            return argValue ?: System.getenv(envKey) ?: error("$envKey must be provided via argument or env var")
        }
    }
}

/**
 * The format in which to log traces in the console.
 */
enum class OutputFormat {
    /* Human-readable plain text output */
    PLAIN_TEXT,
    /* OTLP JSON format following OpenTelemetry specification */
    JSON,
}
