package ai.dev.kit.examples.clients

import ai.dev.kit.clients.instrument
import ai.dev.kit.tracing.ConsoleConfig
import ai.dev.kit.tracing.TracingManager
import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.Model

/**
 * Example of integrating the Anthropic API client [AnthropicClient] with tracing.
 *
 * This example demonstrates how to:
 * - Initialize tracing using [TracingManager] with [ConsoleConfig].
 * - Instrument the Anthropic client using [instrument] to automatically capture trace data.
 * - Perform an Anthropic API request with trace data automatically captured.
 * - Call [TracingManager.flushTraces] before exiting to ensure all trace data is exported.
 *
 * To run this example:
 * * Set the `ANTHROPIC_API_KEY` environment variable to your Anthropic API key.
 *
 * Run the example. Span will appear in the console output.
 */
fun main() {
    TracingManager.setup(ConsoleConfig())
    val apiToken = System.getenv("ANTHROPIC_API_KEY")
        ?: error("Environment variable 'ANTHROPIC_API_KEY' is not set")
    val anthropicClient = AnthropicOkHttpClient.builder().apiKey(apiToken).build()
    val instrumentedClient = instrument(anthropicClient)
    val params = MessageCreateParams.builder()
        .addUserMessage("Generate polite greeting and introduce yourself")
        .temperature(0.0)
        .maxTokens(1000L)
        .model(Model.CLAUDE_OPUS_4_0)
        .build()
    val result = instrumentedClient.messages().create(params).content().first().text().get().text()
    println("Result: $result\nSee trace details in the console.")
    TracingManager.flushTraces()
}