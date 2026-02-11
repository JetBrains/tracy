/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package ai.jetbrains.tracy.core.exporters.otlp

import ai.jetbrains.tracy.core.exporters.ExporterCommonSettings
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.sdk.trace.export.SpanExporter
import java.util.concurrent.TimeUnit

/**
 * Configuration for exporting OpenTelemetry traces via OTLP HTTP.
 *
 * Can be used with any OTLP HTTP-compatible collector such as Jaeger.
 *
 * @param url Base URL of the OTLP HTTP collector endpoint.
 * @property exporterTimeoutSeconds Timeout in seconds for span exporter.
 *  Must be positive. Defaults to [Companion.DEFAULT_EXPORTER_TIMEOUT].
 * @param settings User-provided common settings controlling batching, console logging,
 *  shutdown behavior, and span attribute limits.
 *
 * @see [ExporterCommonSettings]
 * @see [OtlpBaseExporterConfig] for inherited properties such as attribute limits and console logging.
 */
class OtlpHttpExporterConfig(
    url: String,
    exporterTimeoutSeconds: Long = DEFAULT_EXPORTER_TIMEOUT,
    settings: ExporterCommonSettings = ExporterCommonSettings(),
) : OtlpBaseExporterConfig(
    url = url,
    exporterTimeoutSeconds = exporterTimeoutSeconds,
    settings = settings
) {
    override fun createSpanExporter(): SpanExporter {
        return OtlpHttpSpanExporter.builder()
            .setEndpoint("$url/v1/traces")
            .setTimeout(exporterTimeoutSeconds, TimeUnit.SECONDS)
            .build()
    }
}
