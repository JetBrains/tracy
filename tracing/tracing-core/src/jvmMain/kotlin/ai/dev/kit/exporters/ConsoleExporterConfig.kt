package ai.dev.kit.exporters

import io.opentelemetry.exporter.logging.LoggingSpanExporter
import io.opentelemetry.exporter.logging.otlp.OtlpJsonLoggingSpanExporter
import io.opentelemetry.sdk.trace.export.SpanExporter

/**
 * Configuration for exporting OpenTelemetry traces to the console only.
 *
 * @param format The format in which to log traces to the console.
 *
 * @see [OutputFormat]
 * @see [BaseExporterConfig] for configuration of maximum span attributes,
 * maximum attribute value length, and optional console logging.
 * @see [LoggingSpanExporter]
 * @see [OtlpJsonLoggingSpanExporter]
 */
class ConsoleExporterConfig(
    val format: OutputFormat = OutputFormat.PLAIN_TEXT,
    maxNumberOfSpanAttributes: Int? = null,
    maxSpanAttributeValueLength: Int? = null
) : BaseExporterConfig(false, maxNumberOfSpanAttributes, maxSpanAttributeValueLength) {
    override fun createSpanExporter(): SpanExporter = when (format) {
        OutputFormat.PLAIN_TEXT -> LoggingSpanExporter.create()
        OutputFormat.JSON -> OtlpJsonLoggingSpanExporter.create()
        else -> throw IllegalArgumentException("Unsupported console output format: $format")
    }
}
