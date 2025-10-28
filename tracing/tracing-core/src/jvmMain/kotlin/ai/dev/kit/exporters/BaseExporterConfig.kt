package ai.dev.kit.exporters

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
 * @property traceToConsole If `true`, a [SimpleSpanProcessor] is added to log spans
 *  to the console. This is primarily useful for development and debugging.
 *  Default: `false`.
 * @param maxNumberOfSpanAttributes Maximum number of attributes allowed per span.
 *  Defaults to the `MAX_NUMBER_OF_SPAN_ATTRIBUTES` environment variable,
 *  or [DEFAULT_NUMBER_OF_SPAN_ATTRIBUTES] if the environment variable is not set.
 * @param maxSpanAttributeValueLength Maximum allowed length for attribute values.
 *  Defaults to the `MAX_SPAN_ATTRIBUTE_VALUE_LENGTH` environment variable,
 *  or [DEFAULT_SPAN_ATTRIBUTE_VALUE_LENGTH] if the environment variable is not set.
 *
 * @see [SpanExporter]
 * @see [LoggingSpanExporter]
 * @see [SpanLimits]
 */
abstract class BaseExporterConfig(
    protected val traceToConsole: Boolean = false,
    maxNumberOfSpanAttributes: Int? = null,
    maxSpanAttributeValueLength: Int? = null
) {
    protected val resolvedMaxAttributes: Int = maxNumberOfSpanAttributes
        ?: resolveEnvInt("MAX_NUMBER_OF_SPAN_ATTRIBUTES", DEFAULT_NUMBER_OF_SPAN_ATTRIBUTES)
    protected val resolvedMaxAttributeLength: Int = maxSpanAttributeValueLength
        ?: resolveEnvInt("MAX_SPAN_ATTRIBUTE_VALUE_LENGTH", DEFAULT_SPAN_ATTRIBUTE_VALUE_LENGTH)

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
     * - Adds a [SimpleSpanProcessor] logging spans to console if [traceToConsole] is true.
     *
     * @param sdkTracerBuilder the [SdkTracerProviderBuilder] to configure
     */
    open fun addSpanProcessor(sdkTracerBuilder: SdkTracerProviderBuilder) {
        createSpanExporter()?.let { exporter ->
            sdkTracerBuilder.addSpanProcessor(
                BatchSpanProcessor.builder(exporter)
                    .setScheduleDelay(5, TimeUnit.SECONDS)
                    .build()
            )
        }
        if (traceToConsole) {
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
    private fun SdkTracerProviderBuilder.addConsoleLoggingSpanProcessor(): SdkTracerProviderBuilder {
        val spanExporter = LoggingSpanExporter.create()
        addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
        return this
    }

    companion object {
        const val DEFAULT_NUMBER_OF_SPAN_ATTRIBUTES = 256
        const val DEFAULT_SPAN_ATTRIBUTE_VALUE_LENGTH = 8192

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
    PLAIN_TEXT,
    JSON,
}
