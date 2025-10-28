package ai.dev.kit.exporters.http

import ai.dev.kit.exporters.BaseExporterConfig

/**
 * Base configuration class for OpenTelemetry exporters that send spans over HTTP.
 *
 * @property exporterTimeoutSeconds Timeout in seconds for span exporter.
 *  Must be positive. Defaults to [DEFAULT_EXPORTER_TIMEOUT].
 *
 * @see [BaseExporterConfig] for configuration of maximum span attributes,
 * maximum attribute value length, and optional console logging.
 */
abstract class HttpExporterConfig(
    val exporterTimeoutSeconds: Long = DEFAULT_EXPORTER_TIMEOUT,
    traceToConsole: Boolean = false,
    maxNumberOfSpanAttributes: Int? = null,
    maxSpanAttributeValueLength: Int? = null,
) : BaseExporterConfig(traceToConsole, maxNumberOfSpanAttributes, maxSpanAttributeValueLength) {
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
    abstract fun basicAuthHeader(): String
}