package ai.dev.kit.exporters.http

import ai.dev.kit.exporters.BaseExporterConfig
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.sdk.trace.export.SpanExporter
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Configuration for exporting OpenTelemetry traces to [Langfuse](https://langfuse.com) via OTLP HTTP.
 *
 * This class provides all necessary settings to create a [SpanExporter] that sends spans
 * to a Langfuse OTLP endpoint using HTTP with Basic Authentication.
 *
 * @param langfuseUrl Optional base URL of the Langfuse OTLP endpoint.
 *  If not set, it will be retrieved from the `LANGFUSE_URL` environment variable.
 *  Defaults to [LANGFUSE_BASE_URL].
 * @param langfusePublicKey Required Langfuse public API key.
 *  If not provided, it is retrieved from the `LANGFUSE_PUBLIC_KEY` environment variable.
 * @param langfuseSecretKey Required Langfuse secret API key.
 *  If not provided, it is retrieved from the `LANGFUSE_SECRET_KEY` environment variable.
 *
 * @see [HttpExporterConfig] for HTTP-specific exporter configuration.
 * @see [BaseExporterConfig] for inherited properties such as attribute limits and console logging.
 * @see [Langfuse OpenTelemetry Docs](https://langfuse.com/docs/opentelemetry/get-started)
 */

class LangfuseExporterConfig(
    langfuseUrl: String? = null,
    langfusePublicKey: String? = null,
    langfuseSecretKey: String? = null,
    exporterTimeoutSeconds: Long = DEFAULT_EXPORTER_TIMEOUT,
    traceToConsole: Boolean = false,
    maxNumberOfSpanAttributes: Int? = null,
    maxSpanAttributeValueLength: Int? = null,
) : HttpExporterConfig(exporterTimeoutSeconds, traceToConsole, maxNumberOfSpanAttributes, maxSpanAttributeValueLength) {
    val resolvedBaseUrl: String =
        langfuseUrl ?: System.getenv("LANGFUSE_URL") ?: LANGFUSE_BASE_URL
    private val resolvedPublicKey: String =
        resolveRequiredEnvVar(langfusePublicKey, "LANGFUSE_PUBLIC_KEY")
    private val resolvedSecretKey: String =
        resolveRequiredEnvVar(langfuseSecretKey, "LANGFUSE_SECRET_KEY")

    override fun createSpanExporter(): SpanExporter {
        return OtlpHttpSpanExporter.builder()
            .setEndpoint("$resolvedBaseUrl/api/public/otel/v1/traces")
            .setTimeout(exporterTimeoutSeconds, TimeUnit.SECONDS)
            .addHeader("Authorization", "Basic ${basicAuthHeader()}")
            .build()
    }

    override fun basicAuthHeader(): String {
        val credentials = "$resolvedPublicKey:$resolvedSecretKey"
        return Base64.getEncoder().encodeToString(credentials.toByteArray(Charsets.UTF_8))
    }

    companion object {
        private const val LANGFUSE_BASE_URL = "https://cloud.langfuse.com"
    }
}
