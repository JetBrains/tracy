package ai.dev.kit.exporters.otlp

import io.opentelemetry.exporter.internal.http.HttpExporter
import io.opentelemetry.exporter.internal.http.HttpExporterBuilder
import io.opentelemetry.exporter.internal.http.HttpSender
import io.opentelemetry.exporter.internal.marshal.Marshaler
import io.opentelemetry.exporter.internal.otlp.traces.SpanReusableDataMarshaler
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.common.export.MemoryMode
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter
import mu.KotlinLogging
import java.io.InterruptedIOException
import java.net.UnknownHostException
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
 */
internal class ErrorDiagnosingOtlpHttpSpanExporter private constructor(
    private val delegate: HttpExporter<*>,
    private val marshaler: SpanReusableDataMarshaler,
) : SpanExporter {

    override fun export(spans: Collection<SpanData>): CompletableResultCode = marshaler.export(spans)

    override fun flush(): CompletableResultCode = CompletableResultCode.ofSuccess()

    override fun shutdown(): CompletableResultCode = delegate.shutdown()

    companion object {
        private val logger = KotlinLogging.logger {}

        /**
         *
         * See the implementation of [OtlpHttpSpanExporter]
         *
         * @param exporter The [OtlpHttpSpanExporter] instance from which an [HttpExporterBuilder] instance is extracted
         * @param memoryMode The memory mode for span marshaling
         * @param endpointUrl The target endpoint URL for diagnostic messages
         *
         * @see OtlpHttpSpanExporter
         */
        fun create(
            exporter: OtlpHttpSpanExporter,
            endpointUrl: String,
            memoryMode: MemoryMode = MemoryMode.REUSABLE_DATA,
        ): SpanExporter {
            // wrap the HttpSender with our diagnostic version before building
            val exporterClass = OtlpHttpSpanExporter::class.java
            val originalDelegate = exporterClass.getDeclaredField("delegate")
                .also { it.isAccessible = true }
                .get(exporter)

            if (originalDelegate == null || originalDelegate !is HttpExporter<*>) {
                logger.warn { "Failed to extract HttpExporter from OtlpHttpSpanExporter" }
                return exporter
            }

            val diagnosticSender = extractHttpSender(originalDelegate).let { sender ->
                if (sender != null) {
                    DiagnosticHttpSender(delegate = sender, endpointUrl)
                } else {
                    null
                }
            }

            if (diagnosticSender != null) {
                // install our own http sender into the delegate
                patchHttpExporterWithSender(originalDelegate, diagnosticSender)
            } else {
                // the original delegate will be used directly
                logger.warn { "Failed to install diagnostic sender, falling back to default error logging." }
            }

            return ErrorDiagnosingOtlpHttpSpanExporter(
                delegate = originalDelegate,
                marshaler = SpanReusableDataMarshaler(
                    memoryMode,
                    (originalDelegate as HttpExporter<Marshaler>)::export,
                ),
            )
        }

        /**
         * Extract the [HttpSender] from the given [HttpExporter] using reflection.
         * This is necessary because [HttpExporter] doesn't expose its sender.
         */
        private fun extractHttpSender(httpExporter: HttpExporter<*>): HttpSender? {
            val field = HttpExporter::class.java.getDeclaredField("httpSender").also {
                it.isAccessible = true
            }
            val value = field.get(httpExporter)
            return when {
                value == null || value !is HttpSender -> {
                    logger.error { "Failed to extract HttpSender from HttpExporter" }
                    null
                }
                else -> value
            }
        }

        /**
         * Replace [HttpSender] member field of the provided [HttpExporter] instance
         * with the given [httpSender] using reflection.
         */
        private fun patchHttpExporterWithSender(
            delegate: HttpExporter<*>,
            httpSender: HttpSender,
        ) {
            // replace delegate's http sender with the provided one
            val httpExporterClass = HttpExporter::class.java
            httpExporterClass.getDeclaredField("httpSender")
                .also { it.isAccessible = true }
                .set(delegate, httpSender)
        }
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
            val status = response.statusMessage()
            when (statusCode) {
                401 -> logger.warn { buildDiagnosticMessage401(status) }
                403 -> logger.warn { buildDiagnosticMessage403(status) }
                404 -> logger.warn { buildDiagnosticMessage404(status) }
            }
            // continue with the original callback
            onSuccess.accept(response)
        }
        // wrap the failing callback to intercept responses
        val diagnosticsOnError = Consumer<Throwable> { error ->
            when (error) {
                // corresponds to a timeout exception
                is InterruptedIOException -> logger.error { buildDiagnosticMessageTimeout() }
                is UnknownHostException -> logger.error { buildDiagnosticMessageUnknownHost() }
                else -> {
                    logger.error("""
                        | Failed to export traces.
                        | Error message: '${error.message}'
                        | The stacktrace:
                        | ${error.stackTraceToString()}
                    """.trimMargin())
                }
            }
            // continue with the original callback
            onError.accept(error)
        }

        // delegate to the original sender with our wrapped callback
        delegate.send(marshaler, contentLength, diagnosticOnSuccess, diagnosticsOnError)
    }

    override fun shutdown(): CompletableResultCode = delegate.shutdown()

    private fun buildDiagnosticMessage401(statusMessage: String): String = """
        |
        |════════════════════════════════════════════════════════════════════════════════
        |  AUTHENTICATION ERROR (HTTP 401)
        |════════════════════════════════════════════════════════════════════════════════
        |  Target endpoint: $endpointUrl
        |  
        |  Response status message: $statusMessage
        |
        |  Possible causes:
        |  1. Invalid API credentials (public/secret key):
        |     → Verify your LANGFUSE_PUBLIC_KEY and LANGFUSE_SECRET_KEY environment variables
        |     → Ensure credentials are correctly configured in your exporter configuration
        |
        |  2. Incorrect endpoint URL:
        |     → Check your LANGFUSE_URL environment variable
        |     → Default: https://cloud.langfuse.com
        |     → For self-hosted: verify your custom URL is correct
        |
        |  3. Credentials not matching the endpoint:
        |     → Ensure you're using credentials for the correct Langfuse instance
        |     → Self-hosted credentials won't work with cloud.langfuse.com and vice versa
        |
        |  Troubleshooting steps:
        |  - Check if credentials are set
        |  - Verify credentials in Langfuse UI: Settings → API Keys
        |  - Test endpoint connectivity: `curl -I $endpointUrl`
        |════════════════════════════════════════════════════════════════════════════════
    """.trimMargin()

    private fun buildDiagnosticMessage403(statusMessage: String): String = """
        |
        |════════════════════════════════════════════════════════════════════════════════
        |  AUTHORIZATION ERROR (HTTP 403)
        |════════════════════════════════════════════════════════════════════════════════
        |  Target endpoint: $endpointUrl
        |
        |  Your credentials are valid but don't have permission to access this resource.
        |  Response status message: $statusMessage
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

    private fun buildDiagnosticMessage404(statusMessage: String): String = """
        |
        |════════════════════════════════════════════════════════════════════════════════
        |  ENDPOINT NOT FOUND (HTTP 404)
        |════════════════════════════════════════════════════════════════════════════════
        |  Target endpoint: $endpointUrl
        |
        |  The server cannot find the requested resource.
        |  Response status message: $statusMessage
        |
        |  Possible causes:
        |  - Incorrect endpoint URL or path
        |  - Missing /api/public/otel/v1/traces path component
        |  - Wrong base URL (e.g., using HTTP instead of HTTPS)
        |
        |  Expected URL format:
        |  - Langfuse Cloud: https://cloud.langfuse.com/api/public/otel/v1/traces
        |  - Self-hosted: https://your-domain.com/api/public/otel/v1/traces
        |
        |  Troubleshooting steps:
        |  - Verify LANGFUSE_URL environment variable
        |  - Check Langfuse documentation for the correct endpoint URL
        |  - Ensure the path includes /api/public/otel/v1/traces
        |════════════════════════════════════════════════════════════════════════════════
    """.trimMargin()

    private fun buildDiagnosticMessageTimeout(): String = """
        |
        |════════════════════════════════════════════════════════════════════════════════
        |  REQUEST TIMEOUT
        |════════════════════════════════════════════════════════════════════════════════
        |  Target endpoint: $endpointUrl
        |
        |  Failed to export traces to $endpointUrl: request timed out.
        |
        |  Possible causes:
        |  - Network connectivity issues or firewall blocking the connection
        |  - The server is slow to respond or under heavy load
        |  - The endpoint URL is incorrect and the request is hanging
        |
        |  Troubleshooting steps:
        |  - Check your network connection and firewall settings
        |  - When using Langfuse, verify the endpoint URL is correct: LANGFUSE_URL environment variable
        |  - Test connectivity: `curl -I $endpointUrl --max-time 10`
        |  - Consider increasing the timeout configuration if the server is consistently slow
        |  - Check your proxy settings
        |════════════════════════════════════════════════════════════════════════════════
    """.trimMargin()

    private fun buildDiagnosticMessageUnknownHost(): String {
        // Extract hostname from URL for clearer error message
        val hostname = try {
            endpointUrl.substringAfter("://").substringBefore("/")
        } catch (err: Exception) {
            logger.trace(err) {
                "Failed to extract hostname from endpointUrl: $endpointUrl. Using full endpointUrl as hostname."
            }
            endpointUrl
        }

        return """
        |
        |════════════════════════════════════════════════════════════════════════════════
        |  UNKNOWN HOST ERROR
        |════════════════════════════════════════════════════════════════════════════════
        |  Target endpoint: $endpointUrl
        |  Hostname: $hostname
        |
        |  Failed to export traces: the provided hostname $hostname cannot be reached or resolved.
        |
        |  Possible causes:
        |  - The hostname in the URL does not exist or has a typo
        |  - DNS resolution failure - your system cannot resolve the hostname to an IP address
        |  - The server instance is down or no longer available
        |  - Network or DNS configuration issues
        |
        |  Troubleshooting steps:
        |  - Verify the hostname is correct; when using Langfuse, see your LANGFUSE_URL environment variable
        |  - Check for typos in the URL (e.g., with Langfuse: 'langfuse-xxx.labs.jb.gg')
        |  - Test DNS resolution: `nslookup $hostname` or `ping $hostname`
        |  - For cloud.langfuse.com: ensure you have internet connectivity
        |  - For self-hosted: verify the server is running and accessible
        |════════════════════════════════════════════════════════════════════════════════
    """.trimMargin()
    }
}