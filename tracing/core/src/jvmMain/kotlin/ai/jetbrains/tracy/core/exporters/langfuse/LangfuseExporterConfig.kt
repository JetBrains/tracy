package ai.jetbrains.tracy.core.exporters.langfuse

import ai.jetbrains.tracy.core.exporters.BaseExporterConfig
import ai.jetbrains.tracy.core.exporters.ExporterCommonSettings
import ai.jetbrains.tracy.core.exporters.otlp.ErrorDiagnosingOtlpHttpSpanExporter
import ai.jetbrains.tracy.core.exporters.otlp.OtlpBaseExporterConfig
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder
import io.opentelemetry.sdk.trace.SpanProcessor
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import mu.KotlinLogging
import java.util.*
import java.util.concurrent.TimeUnit

private const val LANGFUSE_BASE_URL = "https://cloud.langfuse.com"

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
 * @param settings User-provided common settings controlling batching, console logging,
 *  shutdown behavior, and span attribute limits.
 *
 * @see [ExporterCommonSettings]
 * @see [ai.jetbrains.tracy.core.exporters.otlp.OtlpBaseExporterConfig] for OTLP-specific exporter configuration.
 * @see [BaseExporterConfig] for inherited properties such as attribute limits and console logging.
 * @see [Langfuse OpenTelemetry Docs](https://langfuse.com/docs/opentelemetry/get-started)
 */
class LangfuseExporterConfig(
    langfuseUrl: String? = null,
    langfusePublicKey: String? = null,
    langfuseSecretKey: String? = null,
    exporterTimeoutSeconds: Long = DEFAULT_EXPORTER_TIMEOUT,
    settings: ExporterCommonSettings = ExporterCommonSettings(),
) : OtlpBaseExporterConfig(
    url = langfuseUrl ?: System.getenv("LANGFUSE_URL") ?: LANGFUSE_BASE_URL,
    exporterTimeoutSeconds = exporterTimeoutSeconds,
    settings = settings
) {
    private val resolvedPublicKey: String = resolveRequiredEnvVar(langfusePublicKey, "LANGFUSE_PUBLIC_KEY")
    private val resolvedSecretKey: String = resolveRequiredEnvVar(langfuseSecretKey, "LANGFUSE_SECRET_KEY")
    private val resolvedBasicAuthHeader: String by lazy { basicAuthHeader() }
    private val uploadExceptionHandler = CoroutineExceptionHandler { _, exception ->
        val logger = KotlinLogging.logger {}
        logger.error(exception) { "Failed to upload media content to Langfuse" }
    }

    override fun createSpanExporter(): SpanExporter {
        val endpoint = "$url/api/public/otel/v1/traces"

        val exporter =
            OtlpHttpSpanExporter.builder().setEndpoint(endpoint).setTimeout(exporterTimeoutSeconds, TimeUnit.SECONDS)
                .addHeader("Authorization", "Basic $resolvedBasicAuthHeader").build()

        return ErrorDiagnosingOtlpHttpSpanExporter.create(
            exporter = exporter,
            endpointUrl = endpoint,
        )
    }

    override fun basicAuthHeader(): String {
        val credentials = "$resolvedPublicKey:$resolvedSecretKey"
        return Base64.getEncoder().encodeToString(credentials.toByteArray(Charsets.UTF_8))
    }

    override fun configureSpanProcessors(sdkTracerBuilder: SdkTracerProviderBuilder) {
        // filtering out technical span attributes related to media content
        val filteringSpanExporter = MediaAttributeFilteringSpanExporter(createSpanExporter())

        val langfuseExportingSpanProcessor =
            BatchSpanProcessor.builder(filteringSpanExporter).setScheduleDelay(5, TimeUnit.SECONDS).build()

        val langfuseMediaSpanProcessor = LangfuseMediaSpanProcessor(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + uploadExceptionHandler),
            langfuseUrl = url,
            langfuseBasicAuth = resolvedBasicAuthHeader
        )

        sdkTracerBuilder.addSpanProcessor(
            SpanProcessor.composite(
                // first, upload media content then export to langfuse
                langfuseMediaSpanProcessor,
                langfuseExportingSpanProcessor,
            )
        )

        if (settings.traceToConsole) {
            sdkTracerBuilder.addConsoleLoggingSpanProcessor()
        }
    }
}
