package ai.dev.kit.exporters.otlp

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.sdk.common.export.MemoryMode
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import kotlin.test.assertNotNull
import java.util.concurrent.TimeUnit

/**
 * Comprehensive tests for ErrorDiagnosingOtlpHttpSpanExporter diagnostic logging functionality.
 * 
 * Tests verify that appropriate diagnostic messages are logged for:
 * - HTTP errors (401, 403, 404)
 * - Successful exports (no diagnostic messages)
 */
class ErrorDiagnosingOtlpHttpSpanExporterTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var logAppender: ListAppender<ILoggingEvent>
    private lateinit var logger: Logger

    @BeforeEach
    fun setup() {
        // Start mock web server
        mockWebServer = MockWebServer()
        mockWebServer.start()

        // Setup log capture
        logger = LoggerFactory.getLogger("ai.dev.kit.exporters.otlp") as Logger
        logAppender = ListAppender()
        logAppender.start()
        logger.addAppender(logAppender)
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
        logger.detachAppender(logAppender)
        logAppender.stop()
    }

    @Test
    fun `test HTTP 401 error logs diagnostic message`() = runTest {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("Unauthorized")
        )

        val exporter = createExporter()
        val spanData = createTestSpanData()

        // Act
        exporter.export(listOf(spanData))
            .join(5000, TimeUnit.MILLISECONDS)

        // Assert
        val warningLogs = logAppender.list.filter { it.level == Level.WARN }
        assertTrue(warningLogs.isNotEmpty(), "Expected warning logs for HTTP 401")
        
        val diagnosticLog = warningLogs.firstOrNull { 
            it.formattedMessage.contains("AUTHENTICATION ERROR (HTTP 401)")
        }
        assertNotNull(diagnosticLog, "Expected diagnostic message for HTTP 401")
        assertTrue(
            diagnosticLog.formattedMessage.contains("Invalid API credentials"),
            "Expected credential-related diagnostic message"
        )
        assertTrue(
            diagnosticLog.formattedMessage.contains("LANGFUSE_PUBLIC_KEY"),
            "Expected mention of LANGFUSE_PUBLIC_KEY"
        )
    }

    @Test
    fun `test HTTP 403 error logs diagnostic message`() = runTest {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setBody("Forbidden")
        )

        val exporter = createExporter()
        val spanData = createTestSpanData()

        // Act
        exporter.export(listOf(spanData))
            .join(5000, TimeUnit.MILLISECONDS)

        // Assert
        val warningLogs = logAppender.list.filter { it.level == Level.WARN }
        assertTrue(warningLogs.isNotEmpty(), "Expected warning logs for HTTP 403")
        
        val diagnosticLog = warningLogs.firstOrNull { 
            it.formattedMessage.contains("AUTHORIZATION ERROR (HTTP 403)")
        }
        
        assertNotNull(diagnosticLog, "Expected diagnostic message for HTTP 403")
        assertTrue(
            diagnosticLog.formattedMessage.contains("don't have permission"),
            "Expected permission-related diagnostic message"
        )
    }

    @Test
    fun `test HTTP 404 error logs diagnostic message`() = runTest {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("Not Found")
        )

        val exporter = createExporter()
        val spanData = createTestSpanData()

        // Act
        exporter.export(listOf(spanData))
            .join(5000, TimeUnit.MILLISECONDS)

        // Assert
        val warningLogs = logAppender.list.filter { it.level == Level.WARN }
        assertTrue(warningLogs.isNotEmpty(), "Expected warning logs for HTTP 404")
        
        val diagnosticLog = warningLogs.firstOrNull { 
            it.formattedMessage.contains("ENDPOINT NOT FOUND (HTTP 404)")
        }
        
        assertNotNull(diagnosticLog, "Expected diagnostic message for HTTP 404")
        assertTrue(
            diagnosticLog.formattedMessage.contains("Incorrect endpoint URL"),
            "Expected URL-related diagnostic message"
        )
        assertTrue(
            diagnosticLog.formattedMessage.contains("/api/public/otel/v1/traces"),
            "Expected correct path in diagnostic message"
        )
    }

    @Test
    fun `test successful export does not log error diagnostics`() = runTest {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("OK")
        )

        val exporter = createExporter()
        val spanData = createTestSpanData()

        // Act
        exporter.export(listOf(spanData))
            .join(5000, TimeUnit.MILLISECONDS)

        // Assert - no diagnostic error/warning logs should be present
        val diagnosticLogs = logAppender.list.filter {
            it.formattedMessage.contains("════════════════════════════════") ||
            it.formattedMessage.contains("AUTHENTICATION ERROR") ||
            it.formattedMessage.contains("AUTHORIZATION ERROR") ||
            it.formattedMessage.contains("ENDPOINT NOT FOUND")
        }
        
        assertTrue(
            diagnosticLogs.isEmpty(),
            "No diagnostic error messages should be logged for successful export"
        )
    }

    @Test
    fun `test diagnostic message includes endpoint URL`() = runTest {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("Unauthorized")
        )

        val exporter = createExporter()
        val spanData = createTestSpanData()

        // Act
        exporter.export(listOf(spanData))
            .join(5000, TimeUnit.MILLISECONDS)

        // Assert
        val warningLogs = logAppender.list.filter { it.level == Level.WARN }
        
        val diagnosticLog = warningLogs.firstOrNull { 
            it.formattedMessage.contains("AUTHENTICATION ERROR (HTTP 401)")
        }
        
        assertNotNull(diagnosticLog)
        assertTrue(
            diagnosticLog.formattedMessage.contains(mockWebServer.url("/v1/traces").toString()),
            "Diagnostic message should include the endpoint URL"
        )
    }

    @Test
    fun `test HTTP 500 server error does not log diagnostic message`() = runTest {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error")
        )

        val exporter = createExporter()
        val spanData = createTestSpanData()

        // Act
        exporter.export(listOf(spanData))
            .join(5000, TimeUnit.MILLISECONDS)

        // Assert - Server errors (5xx) should not trigger diagnostic messages
        val diagnosticLogs = logAppender.list.filter {
            it.formattedMessage.contains("════════════════════════════════")
        }
        
        assertTrue(
            diagnosticLogs.isEmpty(),
            "Server errors (5xx) should not trigger custom diagnostic messages"
        )
    }

    /**
     * Helper function to create an ErrorDiagnosingOtlpHttpSpanExporter for testing
     */
    private fun createExporter(): SpanExporter {
        val endpoint = mockWebServer.url("/v1/traces").toString()
        
        val baseExporter = OtlpHttpSpanExporter.builder()
            .setEndpoint(endpoint)
            .setTimeout(5, TimeUnit.SECONDS)
            .build()
        
        return ErrorDiagnosingOtlpHttpSpanExporter(
            exporter = baseExporter,
            memoryMode = MemoryMode.REUSABLE_DATA,
            endpointUrl = endpoint
        )
    }

    /**
     * Helper function to create a test SpanData object
     */
    private fun createTestSpanData(): SpanData {
        return io.opentelemetry.sdk.testing.trace.TestSpanData.builder()
            .setName("test-span")
            .setKind(io.opentelemetry.api.trace.SpanKind.INTERNAL)
            .setStartEpochNanos(System.nanoTime())
            .setEndEpochNanos(System.nanoTime())
            .setStatus(io.opentelemetry.sdk.trace.data.StatusData.ok())
            .setHasEnded(true)
            .build()
    }
}
