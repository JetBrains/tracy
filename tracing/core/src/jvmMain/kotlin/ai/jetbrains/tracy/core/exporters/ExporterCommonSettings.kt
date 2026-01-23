package ai.jetbrains.tracy.core.exporters

import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor

/**
 * Common settings for tracing exporters.
 *
 * Note: This data class is a pure holder of user-provided values. Any environment-variable
 * resolution and defaulting logic is applied by [BaseExporterConfig].
 *
 * @property traceToConsole If `true`, a [SimpleSpanProcessor] is added to log spans
 *  to the console. This is primarily useful for development and debugging.
 *  Default: `false`.
 * @property flushIntervalMs Delay between batch export attempts, in milliseconds.
 *  Default: [DEFAULT_FLUSH_INTERVAL_MS].
 * @property flushThreshold Maximum number of spans per export batch.
 *  Default: [DEFAULT_FLUSH_THRESHOLD].
 * @property flushOnShutdown If `true`, attempts to flush spans on shutdown.
 * Creates Runtime Hook, see [ai.jetbrains.tracy.core.configureOpenTelemetrySdk]
 *  Default: `true`.
 * @property maxNumberOfSpanAttributes Maximum number of attributes allowed per span.
 *  Defaults to the `MAX_NUMBER_OF_SPAN_ATTRIBUTES` environment variable,
 *  or [DEFAULT_NUMBER_OF_SPAN_ATTRIBUTES] if the environment variable is not set.
 * @property maxSpanAttributeValueLength Maximum allowed length for attribute values.
 *  Defaults to the `MAX_SPAN_ATTRIBUTE_VALUE_LENGTH` environment variable,
 *  or [DEFAULT_SPAN_ATTRIBUTE_VALUE_LENGTH] if the environment variable is not set.
 */
data class ExporterCommonSettings(
    val traceToConsole: Boolean = false,
    val flushIntervalMs: Long = DEFAULT_FLUSH_INTERVAL_MS,
    val flushThreshold: Int = DEFAULT_FLUSH_THRESHOLD,
    val flushOnShutdown: Boolean = true,
    val maxNumberOfSpanAttributes: Int? = null,
    val maxSpanAttributeValueLength: Int? = null,
) {
    internal companion object {
        const val DEFAULT_NUMBER_OF_SPAN_ATTRIBUTES = 256
        const val DEFAULT_SPAN_ATTRIBUTE_VALUE_LENGTH = Int.MAX_VALUE
        const val DEFAULT_FLUSH_INTERVAL_MS = 5L
        const val DEFAULT_FLUSH_THRESHOLD = 512
    }
}