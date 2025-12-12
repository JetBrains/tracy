package ai.dev.kit.exporters.otlp

import ai.dev.kit.exporters.BaseExporterConfig
import ai.dev.kit.exporters.ExporterCommonSettings

/**
 * Base configuration class for OpenTelemetry exporters that send spans over HTTP.
 *
 * @property exporterTimeoutSeconds Timeout in seconds for span exporter.
 *  Must be positive. Defaults to [DEFAULT_EXPORTER_TIMEOUT].
 * @param settings User-provided common settings controlling batching, console logging,
 *  shutdown behavior, and span attribute limits.
 *
 * @see [ExporterCommonSettings]
 * @see [BaseExporterConfig]
 */
abstract class OtlpBaseExporterConfig(
    val url: String,
    val exporterTimeoutSeconds: Long = DEFAULT_EXPORTER_TIMEOUT,
    settings: ExporterCommonSettings = ExporterCommonSettings(),
) : BaseExporterConfig(settings) {
    companion object {
        /* Default timeout for sending spans, in seconds */
        const val DEFAULT_EXPORTER_TIMEOUT = 10L
    }

    init {
        require(exporterTimeoutSeconds > 0) { "Exporter timeout must be positive" }
    }

    /**
     * Returns the HTTP Basic Authentication header value for this exporter.
     */
    open fun basicAuthHeader(): String? = null
}