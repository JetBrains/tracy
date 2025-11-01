package ai.dev.kit.exporters

import ai.dev.kit.tracing.ConsoleOutputFormat
import ai.dev.kit.tracing.FileConfig
import io.opentelemetry.exporter.logging.LoggingSpanExporter
import io.opentelemetry.exporter.logging.otlp.OtlpJsonLoggingSpanExporter
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.util.concurrent.TimeUnit
import java.util.logging.FileHandler
import java.util.logging.Formatter
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import java.util.logging.SimpleFormatter
import kotlin.jvm.javaClass


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
        internal fun create(config: FileConfig): OtlpFileSpanExporter {
            val exporter = when (config.format) {
                ConsoleOutputFormat.PLAIN_TEXT -> LoggingSpanExporter.create()
                ConsoleOutputFormat.JSON -> OtlpJsonLoggingSpanExporter.create()
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

            return loggerField.get(exporter) as? Logger
                ?: throw IllegalStateException("Field 'logger' is null")
        }

        /**
         * Re-configures the provided [Logger] instance to output
         * traces into a file according to the configuration [FileConfig].
         */
        private fun reconfigureLogger(logger: Logger, config: FileConfig) {
            logger.setUseParentHandlers(false)
            val fileHandler = FileHandler(config.filepath, config.append)

            fileHandler.formatter = when (config.format) {
                ConsoleOutputFormat.PLAIN_TEXT -> SimpleFormatter()
                ConsoleOutputFormat.JSON -> JsonSpanFormatter()
            }

            fileHandler.setLevel(Level.INFO)
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
                    serializer = JsonElement.serializer(),
                    value = Json.parseToJsonElement(
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

fun SdkTracerProviderBuilder.addOtlpFileSpanProcessor(
    config: FileConfig,
): SdkTracerProviderBuilder {
    val spanExporter = OtlpFileSpanExporter.create(config)
    addSpanProcessor(
        BatchSpanProcessor.builder(spanExporter)
            .setScheduleDelay(3, TimeUnit.SECONDS)
            .build()
    )
    return this
}
