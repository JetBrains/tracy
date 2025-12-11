package ai.dev.kit.exporters.otlp

import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.sdk.trace.export.SpanExporter
import java.util.concurrent.TimeUnit

/**
 * Configuration for exporting OpenTelemetry traces via OTLP HTTP.
 *
 * Can be used with any OTLP HTTP-compatible collector such as Jaeger.
 *
 * @param url Base URL of the OTLP HTTP collector endpoint.
 *
 * @see [OtlpBaseExporterConfig] for inherited properties such as attribute limits and console logging.
 */
class OtlpHttpExporterConfig(
    url: String,
    exporterTimeoutSeconds: Long = DEFAULT_EXPORTER_TIMEOUT,
    traceToConsole: Boolean = false,
    maxNumberOfSpanAttributes: Int? = null,
    maxSpanAttributeValueLength: Int? = null,
) : OtlpBaseExporterConfig(
    url = url,
    exporterTimeoutSeconds = exporterTimeoutSeconds,
    traceToConsole = traceToConsole,
    maxNumberOfSpanAttributes = maxNumberOfSpanAttributes,
    maxSpanAttributeValueLength = maxSpanAttributeValueLength
) {
    override fun createSpanExporter(): SpanExporter {
        return OtlpHttpSpanExporter.builder()
            .setEndpoint("$url/v1/traces")
            .setTimeout(exporterTimeoutSeconds, TimeUnit.SECONDS)
            .build()
    }
}
