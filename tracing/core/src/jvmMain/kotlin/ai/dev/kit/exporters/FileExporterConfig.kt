package ai.dev.kit.exporters

import io.opentelemetry.exporter.logging.LoggingSpanExporter
import io.opentelemetry.exporter.logging.otlp.OtlpJsonLoggingSpanExporter
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.util.logging.*

/**
 * Configuration for exporting OpenTelemetry traces to a file.
 *
 * @param filepath The file path where traces should be written (e.g., `tracing.log` or `traces.jsonl`).
 * @param append Whether to append to the file if it exists, or create a new file.
 * @param format The format in which to log traces to the file or console.
 * @param traceToConsole If true, also logs spans to the console for debugging.
 *        Default: false.
 *
 * @see [BaseExporterConfig] for configuration of maximum span attributes,
 * maximum attribute value length, and optional console logging.
 * @see [OtlpFileSpanExporter]
 */
class FileExporterConfig(
    val filepath: String,
    val append: Boolean,
    val format: OutputFormat = OutputFormat.PLAIN_TEXT,
    traceToConsole: Boolean = false,
    maxNumberOfSpanAttributes: Int? = null,
    maxSpanAttributeValueLength: Int? = null
) : BaseExporterConfig(traceToConsole, maxNumberOfSpanAttributes, maxSpanAttributeValueLength) {
    override fun createSpanExporter(): SpanExporter = OtlpFileSpanExporter.create(this)
}

/**
 * Extracts traces into the configured file in either JSON or plain text formats.
 *
 * Delegates to either [LoggingSpanExporter] or [OtlpJsonLoggingSpanExporter] for
 * plain text and JSON output formats respectively.
 *
 * See: [OpenTelemetry Protocol File Exporter](https://opentelemetry.io/docs/specs/otel/protocol/file-exporter/)
 *
 * @see LoggingSpanExporter
 * @see OtlpJsonLoggingSpanExporter
 */
class OtlpFileSpanExporter private constructor(
    private val delegate: SpanExporter
) : SpanExporter {
    companion object {
        /**
         * Creates a configured OpenTelemetry span exporter instance
         * of [OtlpFileSpanExporter] that writes traces to a file.
         */
        internal fun create(config: FileExporterConfig): OtlpFileSpanExporter {
            val exporter = when (config.format) {
                OutputFormat.PLAIN_TEXT -> LoggingSpanExporter.create()
                OutputFormat.JSON -> OtlpJsonLoggingSpanExporter.create()
            }
            // patch logger to output into a file
            val logger = getExporterLogger(exporter)
            reconfigureLogger(logger, config)

            return OtlpFileSpanExporter(exporter)
        }

        /**
         * Extracts logger instance of the [SpanExporter]
         */
        private fun getExporterLogger(exporter: SpanExporter): Logger {
            val cls = exporter.javaClass

            val loggerField = cls.getDeclaredField("logger")
            loggerField.isAccessible = true

            return loggerField.get(exporter) as? Logger ?: throw IllegalStateException("Field 'logger' is null")
        }

        /**
         * Re-configures the provided [Logger] instance to output
         * traces into a file according to the configuration [FileExporterConfig].
         */
        private fun reconfigureLogger(logger: Logger, config: FileExporterConfig) {
            logger.setUseParentHandlers(false)
            val fileHandler = FileHandler(config.filepath, config.append)

            fileHandler.formatter = when (config.format) {
                OutputFormat.PLAIN_TEXT -> SimpleFormatter()
                OutputFormat.JSON -> JsonSpanFormatter()
            }

            fileHandler.level = Level.INFO
            logger.setLevel(Level.INFO)

            logger.addHandler(fileHandler)
        }

        /**
         * Formats spans in a JSON structure complying with
         * the Telemetry data requirements of the **OpenTelemetry Protocol File Exporter**.
         *
         * See: [OpenTelemetry Protocol File Exporter](https://opentelemetry.io/docs/specs/otel/protocol/file-exporter/)
         */
        private class JsonSpanFormatter : Formatter() {
            companion object {
                private val minifiedJson = Json { prettyPrint = false }
            }

            override fun format(record: LogRecord): String {
                val minimizedStringifiedMessage = minifiedJson.encodeToString(
                    serializer = JsonElement.serializer(), value = Json.parseToJsonElement(
                        """
                            {
                                "resourceSpans": [
                                    ${record.message}
                               ]
                            }
                        """.trimIndent()
                    )
                )
                return minimizedStringifiedMessage
            }
        }
    }

    override fun export(spans: Collection<SpanData?>): CompletableResultCode? {
        return delegate.export(spans)
    }

    override fun flush(): CompletableResultCode? {
        return delegate.flush()
    }

    override fun shutdown(): CompletableResultCode? {
        return delegate.shutdown()
    }
}