package ai.dev.kit.exporters.http

import io.opentelemetry.exporter.internal.http.HttpExporter
import io.opentelemetry.exporter.internal.http.HttpExporterBuilder
import io.opentelemetry.exporter.internal.http.HttpSender
import io.opentelemetry.exporter.internal.marshal.Marshaler
import io.opentelemetry.exporter.internal.otlp.traces.SpanReusableDataMarshaler
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.common.export.MemoryMode
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter
import io.opentelemetry.sdk.internal.StandardComponentId
import io.opentelemetry.sdk.common.InternalTelemetryVersion
import java.util.function.Supplier
import mu.KotlinLogging
import java.util.function.Consumer

/**
 * Custom OTLP HTTP Span Exporter with enhanced error diagnostics.
 *
 * This exporter wraps the standard OpenTelemetry HTTP exporter and provides
 * better error messages when authentication or configuration issues occur.
 *
 * Common issues diagnosed:
 * - HTTP 401: Invalid API key/credentials or wrong endpoint URL
 * - HTTP 403: Valid credentials but insufficient permissions
 * - HTTP 404: Wrong endpoint URL or path
 *
 * @param builder The [HttpExporterBuilder] used to construct the delegate exporter
 * @param memoryMode The memory mode for span marshaling
 * @param endpointUrl The target endpoint URL for diagnostic messages
 */
class CustomOtlpHttpSpanExporter(
    builder: HttpExporterBuilder<Marshaler>,
    memoryMode: MemoryMode,
    private val endpointUrl: String,
) : SpanExporter {
    private val delegate: HttpExporter<Marshaler>
    private val marshaler: SpanReusableDataMarshaler

    init {
        // Wrap the HttpSender with our diagnostic version before building
        val originalSender = builder.build()
        val diagnosticSender = DiagnosticHttpSender(
            delegate = extractHttpSender(originalSender),
            endpointUrl = endpointUrl
        )

        // Build a new HttpExporter with our diagnostic sender
        delegate = createHttpExporterWithSender(builder, diagnosticSender)
        marshaler = SpanReusableDataMarshaler(memoryMode, delegate::export)
    }

    override fun export(spans: Collection<SpanData>): CompletableResultCode = marshaler.export(spans)

    override fun flush(): CompletableResultCode = CompletableResultCode.ofSuccess()

    override fun shutdown(): CompletableResultCode = delegate.shutdown()

    /**
     * Extract the [HttpSender] from a built [HttpExporter] using reflection.
     * This is necessary because [HttpExporter] doesn't expose its sender.
     */
    private fun extractHttpSender(httpExporter: HttpExporter<Marshaler>): HttpSender {
        val field = HttpExporter::class.java.getDeclaredField("httpSender").also {
            it.isAccessible = true
        }
        return field.get(httpExporter) as HttpSender
    }

    /**
     * Create a new [HttpExporter] with a custom [HttpSender] using reflection.
     */
    private fun createHttpExporterWithSender(
        builder: HttpExporterBuilder<Marshaler>,
        httpSender: HttpSender,
    ): HttpExporter<Marshaler> {
        /*
        // build a temporary exporter to get the builder's internal state
        val tempExporter = builder.build()

        // extract the necessary fields from the temp exporter
        val componentIdField = HttpExporter::class.java.getDeclaredField("type").also {
            it.isAccessible = true
        }
        val exporterMetricsField = HttpExporter::class.java.getDeclaredField("exporterMetrics").also {
            it.isAccessible = true
        }
        */

        // access builder's internal fields to recreate the HttpExporter constructor parameters
        val builderClass = HttpExporterBuilder::class.java

        val componentId = builderClass.getDeclaredField("componentId")
            .also { it.isAccessible = true }.get(builder)

        val meterProviderSupplier = builderClass.getDeclaredField("meterProviderSupplier")
            .also { it.isAccessible = true }.get(builder)

        val internalTelemetryVersion = builderClass.getDeclaredField("internalTelemetryVersion").also { it.isAccessible = true }.get(builder)

        val endpoint = builderClass.getDeclaredField("endpoint")
            .also { it.isAccessible = true }.get(builder) as String

        // create a new HttpExporter with our custom sender
        val constructor = HttpExporter::class.java.getDeclaredConstructor(
            StandardComponentId::class.java,
            HttpSender::class.java,
            Supplier::class.java,
            InternalTelemetryVersion::class.java,
            String::class.java,
        ).also { it.isAccessible = true }

        return constructor.newInstance(
            componentId,
            httpSender,
            meterProviderSupplier,
            internalTelemetryVersion,
            endpoint,
        ) as HttpExporter<Marshaler>
    }
}

/**
 * [HttpSender] wrapper that provides enhanced diagnostic messages for common HTTP errors.
 *
 * This sender intercepts HTTP responses and logs helpful diagnostic messages when
 * authentication or configuration errors occur, before the default error logging happens.
 */
private class DiagnosticHttpSender(
    private val delegate: HttpSender,
    private val endpointUrl: String,
) : HttpSender {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun send(
        marshaler: Marshaler,
        contentLength: Int,
        onSuccess: Consumer<HttpSender.Response>,
        onError: Consumer<Throwable>
    ) {
        // wrap the success callback to intercept responses
        val diagnosticOnSuccess = Consumer<HttpSender.Response> { response ->
            val statusCode = response.statusCode()
            // provide diagnostic logging for specific error codes
            when (statusCode) {
                401 -> logger.warn { buildDiagnosticMessage401() }
                403 -> logger.warn { buildDiagnosticMessage403() }
                404 -> logger.warn { buildDiagnosticMessage404() }
            }
            // continue with the original callback (which will log the standard error)
            onSuccess.accept(response)
        }
        // delegate to the original sender with our wrapped callback
        delegate.send(marshaler, contentLength, diagnosticOnSuccess, onError)
    }

    override fun shutdown(): CompletableResultCode = delegate.shutdown()

    private fun buildDiagnosticMessage401(): String = """
        |
        |════════════════════════════════════════════════════════════════════════════════
        |  AUTHENTICATION ERROR (HTTP 401)
        |════════════════════════════════════════════════════════════════════════════════
        |  Target endpoint: $endpointUrl
        |
        |  Possible causes:
        |  1. Invalid API credentials (public/secret key):
        |     → Verify your LANGFUSE_PUBLIC_KEY and LANGFUSE_SECRET_KEY environment variables
        |     → Ensure credentials are correctly configured in your exporter configuration
        |
        |  2. Incorrect endpoint URL:
        |     → Check your `LANGFUSE_URL` environment variable
        |     → Default: https://cloud.langfuse.com
        |     → For self-hosted: verify your custom URL is correct
        |
        |  3. Credentials not matching the endpoint:
        |     → Ensure you're using credentials for the correct Langfuse instance
        |     → Self-hosted credentials won't work with `cloud.langfuse.com` and vice versa
        |
        |  Troubleshooting steps:
        |  - Check if credentials are set
        |  - Verify credentials in Langfuse UI: Settings → API Keys
        |  - Test endpoint connectivity: `curl -I $endpointUrl`
        |════════════════════════════════════════════════════════════════════════════════
    """.trimMargin()

    private fun buildDiagnosticMessage403(): String = """
        |
        |════════════════════════════════════════════════════════════════════════════════
        |  AUTHORIZATION ERROR (HTTP 403)
        |════════════════════════════════════════════════════════════════════════════════
        |  Target endpoint: $endpointUrl
        |
        |  Your credentials are valid but don't have permission to access this resource.
        |
        |  Possible causes:
        |  - API key doesn't have sufficient permissions
        |  - Project or organization access restrictions
        |
        |  Troubleshooting steps:
        |  - Verify API key permissions in Langfuse UI: Settings → API Keys
        |  - Check if your account has access to the target project
        |════════════════════════════════════════════════════════════════════════════════
    """.trimMargin()

    private fun buildDiagnosticMessage404(): String = """
        |
        |════════════════════════════════════════════════════════════════════════════════
        |  ENDPOINT NOT FOUND (HTTP 404)
        |════════════════════════════════════════════════════════════════════════════════
        |  Target endpoint: $endpointUrl
        |
        |  The server cannot find the requested resource.
        |
        |  Possible causes:
        |  - Incorrect endpoint URL or path
        |  - Missing `/api/public/otel/v1/traces` path component
        |  - Wrong base URL (e.g., using HTTP instead of HTTPS)
        |
        |  Expected URL format:
        |  - Langfuse Cloud: https://cloud.langfuse.com/api/public/otel/v1/traces
        |  - Self-hosted: https://your-domain.com/api/public/otel/v1/traces
        |
        |  Troubleshooting steps:
        |  - Verify `LANGFUSE_URL` environment variable
        |  - Check Langfuse documentation for the correct endpoint URL
        |  - Ensure the path includes `/api/public/otel/v1/traces`
        |════════════════════════════════════════════════════════════════════════════════
    """.trimMargin()
}
