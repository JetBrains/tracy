package ai.dev.kit.exporters.otlp

import ai.dev.kit.exporters.BaseExporterConfig
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.sdk.trace.export.SpanExporter
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Configuration for exporting OpenTelemetry traces to [W&B Weave](https://wandb.ai/site/weave) via OTLP HTTP.
 *
 * This class provides all necessary settings to create a [SpanExporter] that sends spans
 * to a Weave OTLP endpoint using HTTP with Basic Authentication.
 *
 * @param baseUrl Optional base URL of the Weave OTLP endpoint.
 *  If not set, it will be retrieved from the `WEAVE_URL` environment variable.
 *  Defaults to [WEAVE_BASE_URL].
 * @param entity Required W&B entity (team/org) name.
 *  Can be found on your W&B dashboard at [https://wandb.ai/home] under *Teams*.
 *  If not provided, it is retrieved from the `WEAVE_ENTITY` environment variable.
 * @param projectName Required W&B project name.
 *  If not provided, it is retrieved from the `WEAVE_PROJECT_NAME` environment variable.
 * @param apiKey Required W&B API key.
 *  Can be created on [https://wandb.ai/authorize].
 *  If not provided, it is retrieved from the `WEAVE_API_KEY` environment variable.
 *
 * @see [OtlpBaseExporterConfig] for OTLP-specific exporter configuration.
 * @see [BaseExporterConfig] for inherited properties such as attribute limits, console logging, and exporter timeout.
 * @see [Weave OpenTelemetry Docs](https://weave-docs.wandb.ai/guides/tracking/otel/)
 */
class WeaveExporterConfig(
    baseUrl: String? = null,
    entity: String? = null,
    projectName: String? = null,
    apiKey: String? = null,
    exporterTimeoutSeconds: Long = DEFAULT_EXPORTER_TIMEOUT,
    traceToConsole: Boolean = false,
    maxNumberOfSpanAttributes: Int? = null,
    maxSpanAttributeValueLength: Int? = null,
) : OtlpBaseExporterConfig(
    url = baseUrl ?: System.getenv("WEAVE_URL") ?: WEAVE_BASE_URL,
    exporterTimeoutSeconds = exporterTimeoutSeconds,
    traceToConsole = traceToConsole,
    maxNumberOfSpanAttributes = maxNumberOfSpanAttributes,
    maxSpanAttributeValueLength = maxSpanAttributeValueLength
) {
    private val resolvedEntity: String = resolveRequiredEnvVar(entity, "WEAVE_ENTITY")
    private val resolvedProjectName: String = resolveRequiredEnvVar(projectName, "WEAVE_PROJECT_NAME")
    private val resolvedApiKey: String = resolveRequiredEnvVar(apiKey, "WEAVE_API_KEY")

    override fun createSpanExporter(): SpanExporter {
        return OtlpHttpSpanExporter.builder().setEndpoint("$url/otel/v1/traces")
            .setTimeout(exporterTimeoutSeconds, TimeUnit.SECONDS)
            .addHeader("project_id", "$resolvedEntity/$resolvedProjectName")
            .addHeader("Authorization", "Basic ${basicAuthHeader()}")
            .build()
    }

    override fun basicAuthHeader(): String {
        val credentials = "api:$resolvedApiKey"
        return Base64.getEncoder().encodeToString(credentials.toByteArray(Charsets.UTF_8))
    }

    companion object {
        private const val WEAVE_BASE_URL = "https://trace.wandb.ai"
    }
}
