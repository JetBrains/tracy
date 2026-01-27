package ai.jetbrains.tracy.examples.clients

import ai.jetbrains.tracy.gemini.clients.instrument
import ai.jetbrains.tracy.core.exporters.ConsoleExporterConfig
import ai.jetbrains.tracy.core.tracing.TracingManager
import ai.jetbrains.tracy.core.tracing.configureOpenTelemetrySdk
import com.google.genai.Client
import com.google.genai.types.GenerateContentConfig

/**
 * Example of integrating the Google Gemini API [Client] client with tracing.
 *
 * This example demonstrates how to:
 * - Initialize tracing using [TracingManager] with [ConsoleExporterConfig].
 * - Instrument the Gemini client using [instrument] to automatically capture trace data.
 * - Perform a Gemini API request with trace data automatically captured.
 * - Traces are automatically flushed based on [ExporterCommonSettings][ai.jetbrains.tracy.core.exporters.ExporterCommonSettings]
 *   (periodically via `flushIntervalMs`/`flushThreshold`, and on shutdown if `flushOnShutdown = true`).
 * - For manual control, call [TracingManager.flushTraces] to ensure all trace data is exported immediately.
 *
 * To run this example:
 * * Set the `GEMINI_API_KEY` environment variable to your Gemini API key.
 *
 * Run the example. Span will appear in the console output.
 */
fun main() {
    TracingManager.setSdk(configureOpenTelemetrySdk(ConsoleExporterConfig()))
    TracingManager.traceSensitiveContent()

    val apiToken = System.getenv("GEMINI_API_KEY") ?: error("Environment variable 'GEMINI_API_KEY' is not set")
    val geminiClient = Client.builder().apiKey(apiToken).build()
    val instrumentedClient = instrument(geminiClient)
    val result = instrumentedClient.models.generateContent(
        "gemini-2.5-flash",
        "Generate polite greeting and introduce yourself",
        GenerateContentConfig.builder().temperature(0.0f).build()
    )
    println("Result: $result\nSee trace details in the console.")
    // Manual flush - alternatively, configure automatic flushing via ExporterCommonSettings
    TracingManager.flushTraces()
}