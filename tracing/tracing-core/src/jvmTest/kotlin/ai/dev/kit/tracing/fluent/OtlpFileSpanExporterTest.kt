package ai.dev.kit.tracing.fluent

import ai.dev.kit.exporters.OtlpFileSpanExporter
import ai.dev.kit.tracing.BaseOpenTelemetryTracingTest
import ai.dev.kit.tracing.ConsoleOutputFormat
import ai.dev.kit.tracing.FileTracingConfig
import ai.dev.kit.tracing.NoLoggingConfig
import ai.dev.kit.tracing.TracingManager
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.io.IOException


/*
class JsonOnlyFormatter : Formatter() {
    companion object {
        private val minifiedJson = Json { prettyPrint = false }
    }

    override fun format(record: LogRecord): String {
        val minimizedStringifiedMessage = minifiedJson.encodeToString(
            serializer = JsonElement.serializer(),
            value = Json.parseToJsonElement(
                """
                    {
                        "resourceSpans": [
                            ${record.message}
                       ]
                    }
                """.trimIndent()
            )
        )
        return minimizedStringifiedMessage
    }
}
*/

@Throws(IOException::class)
private fun setupWithProgrammaticConfig(): OpenTelemetry {
    val config = FileTracingConfig(
        filepath = "/Users/vartiukhov/dev/jetbrains/ai-development-experience/ai-dev-kit/otel-spans.log",
        append = true,
        format = ConsoleOutputFormat.JSON,
    )
    val loggingExporter = OtlpFileSpanExporter.create(config)

    /*
    // Get the logger used by LoggingSpanExporter
    // val exporterClass = LoggingSpanExporter::class
    val exporterClass = OtlpJsonLoggingSpanExporter::class
    val logger: Logger = Logger.getLogger(exporterClass.qualifiedName)

    // Remove default console handler to avoid duplicate output
    logger.setUseParentHandlers(false)

    // Create a FileHandler that writes to a file
    val filepath = "/Users/vartiukhov/dev/jetbrains/ai-development-experience/ai-dev-kit/otel-spans.log"
    val fileHandler = FileHandler(filepath, true) // true = append mode

    // Optional: Set a formatter for better readability
    // fileHandler.setFormatter(SimpleFormatter())
    fileHandler.formatter = JsonOnlyFormatter()

    // Set the log level
    fileHandler.setLevel(Level.INFO)
    logger.setLevel(Level.INFO)
    // Add the file handler to the logger
    logger.addHandler(fileHandler)

    // Create the LoggingSpanExporter
    // val loggingExporter = LoggingSpanExporter.create()
    val loggingExporter = OtlpJsonLoggingSpanExporter.create()
    */

    // Configure OpenTelemetry
    val resource = Resource.getDefault()
        .merge(
            Resource.create(
                Attributes.of(AttributeKey.stringKey("service.name"), "ai-development-kit")
            )
        )

    val sdkTracerProvider = SdkTracerProvider.builder()
        .addSpanProcessor(SimpleSpanProcessor.create(loggingExporter))
        .setResource(resource)
        .build()

    return OpenTelemetrySdk.builder()
        .setTracerProvider(sdkTracerProvider)
        .build()
}


class OtlpFileSpanExporterTest : BaseOpenTelemetryTracingTest() {
    @Test
    fun test2() = runTest {
        val sdk = setupWithProgrammaticConfig()
        val tracer = sdk.getTracer("integration-test")

        val span = tracer.spanBuilder("my-test-span").startSpan()
        span.makeCurrent().use {
            span.setAttribute("key1", "value1")
            span.setAttribute("key2", "value2")
        }
        span.end()
    }

    @Test
    fun test1() = runTest {

        val config = NoLoggingConfig(
            traceToConsole = true,
            format = ConsoleOutputFormat.PLAIN_TEXT,
        )

        TracingManager.setup(config)
        val tracer = TracingManager.tracer
        val span = tracer.spanBuilder("my-test-span-123").startSpan()

        try {
            span.makeCurrent().use {
                span.setAttribute("key1", "value1")
            }
        }
        finally {
            span.end()
        }
        TracingManager.flushTraces(10)
    }
}