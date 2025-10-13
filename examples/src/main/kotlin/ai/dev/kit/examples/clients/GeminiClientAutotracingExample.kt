package ai.dev.kit.examples.clients

import ai.dev.kit.clients.instrument
import ai.dev.kit.tracing.ConsoleConfig
import ai.dev.kit.tracing.TracingManager
import com.google.genai.Client
import com.google.genai.types.GenerateContentConfig

/**
 * Example of integrating the Google Gemini API [Client] client with tracing.
 *
 * This example demonstrates how to:
 * - Initialize tracing using [TracingManager] with [ConsoleConfig].
 * - Instrument the Gemini client using [instrument] to automatically capture trace data.
 * - Perform a Gemini API request with trace data automatically captured.
 * - Call [TracingManager.flushTraces] before exiting to ensure all trace data is exported.
 *
 * To run this example:
 * * Set the `GEMINI_API_KEY` environment variable to your Gemini API key.
 *
 * Run the example. Span will appear in the console output.
 */
fun main() {
    TracingManager.setup(ConsoleConfig())
    val apiToken = System.getenv("GEMINI_API_KEY")
        ?: error("Environment variable 'GEMINI_API_KEY' is not set")
    val geminiClient = Client.builder().apiKey(apiToken).build()
    val instrumentedClient = instrument(geminiClient)
    val result = instrumentedClient.models.generateContent(
        "gemini-2.5-flash",
        "Generate polite greeting and introduce yourself",
        GenerateContentConfig.builder().temperature(0.0f).build()
    )
    println("Result: $result\nSee trace details in the console.")
    TracingManager.flushTraces()
}