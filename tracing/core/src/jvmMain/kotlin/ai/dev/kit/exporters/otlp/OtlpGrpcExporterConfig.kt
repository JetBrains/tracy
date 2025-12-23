package ai.dev.kit.exporters.otlp

import ai.dev.kit.exporters.ExporterCommonSettings
import ai.dev.kit.exporters.otlp.OtlpBaseExporterConfig.Companion.DEFAULT_EXPORTER_TIMEOUT
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.sdk.trace.export.SpanExporter
import java.util.concurrent.TimeUnit

/**
 * Configuration for exporting OpenTelemetry traces via OTLP gRPC.
 *
 * Compatible with any OTLP gRPC-capable collector (e.g., Jaeger).
 *
 * @param url Base endpoint of the OTLP gRPC collector.
 * @property exporterTimeoutSeconds Timeout for the span exporter, in seconds.
 *  Must be positive. Defaults to [DEFAULT_EXPORTER_TIMEOUT].
 * @param settings Common exporter settings for batching, console logging,
 *  shutdown behavior, and span attribute limits.
 *
 * @see ExporterCommonSettings
 * @see OtlpBaseExporterConfig for inherited options like attribute limits and console logging.
 */
class OtlpGrpcExporterConfig(
    url: String,
    exporterTimeoutSeconds: Long = DEFAULT_EXPORTER_TIMEOUT,
    settings: ExporterCommonSettings = ExporterCommonSettings(),
) : OtlpBaseExporterConfig(
    url = url,
    exporterTimeoutSeconds = exporterTimeoutSeconds,
    settings = settings
) {
    override fun createSpanExporter(): SpanExporter {
        return OtlpGrpcSpanExporter.builder()
            .setEndpoint(url)
            .setTimeout(exporterTimeoutSeconds, TimeUnit.SECONDS)
            .build()
    }
}
