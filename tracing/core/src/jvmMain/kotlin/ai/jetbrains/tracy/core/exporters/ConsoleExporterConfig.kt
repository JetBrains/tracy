package ai.jetbrains.tracy.core.exporters

import io.opentelemetry.exporter.logging.LoggingSpanExporter
import io.opentelemetry.exporter.logging.otlp.OtlpJsonLoggingSpanExporter
import io.opentelemetry.sdk.trace.export.SpanExporter

/**
 * Configuration for exporting OpenTelemetry traces to the console only.
 *
 * @param format The format in which to log traces to the console.
 * @param settings User-provided common settings controlling batching, console logging,
 *  shutdown behavior, and span attribute limits.
 *
 * @see [OutputFormat]
 * @see [ExporterCommonSettings]
 * @see [BaseExporterConfig]
 * @see [LoggingSpanExporter]
 * @see [OtlpJsonLoggingSpanExporter]
 */
class ConsoleExporterConfig(
    val format: OutputFormat = OutputFormat.PLAIN_TEXT,
    settings: ExporterCommonSettings = ExporterCommonSettings(),
) : BaseExporterConfig(settings) {
    override fun createSpanExporter(): SpanExporter = when (format) {
        OutputFormat.PLAIN_TEXT -> LoggingSpanExporter.create()
        OutputFormat.JSON -> OtlpJsonLoggingSpanExporter.create()
    }
}
