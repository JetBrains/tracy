package ai.jetbrains.tracy.examples.clients

import ai.jetbrains.tracy.openai.clients.instrument
import ai.jetbrains.tracy.core.exporters.ConsoleExporterConfig
import ai.jetbrains.tracy.core.tracing.TracingManager
import ai.jetbrains.tracy.core.tracing.configureOpenTelemetrySdk
import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.models.ChatModel
import com.openai.models.chat.completions.ChatCompletionCreateParams

/**
 * Example of integrating the OpenAI API [OpenAIClient] client with tracing.
 *
 * This example demonstrates how to:
 * - Initialize tracing using [TracingManager] with [ConsoleExporterConfig].
 * - Instrument the OpenAI client using [instrument] to automatically capture trace data.
 * - Execute an OpenAI API call with tracing information automatically collected.
 * - Traces are automatically flushed based on [ExporterCommonSettings][ai.jetbrains.tracy.core.exporters.ExporterCommonSettings]
 *   (periodically via `flushIntervalMs`/`flushThreshold`, and on shutdown if `flushOnShutdown = true`).
 * - For manual control, call [TracingManager.flushTraces] to ensure all trace data is exported immediately.
 *
 * To run this example:
 * * Set the `OPENAI_API_KEY` environment variable to your OpenAI API key.
 *
 * Run the example. Request and response spans will appear in the console output.
 */
fun main() {
    TracingManager.setSdk(configureOpenTelemetrySdk(ConsoleExporterConfig()))
    TracingManager.traceSensitiveContent()

    val apiToken = System.getenv("OPENAI_API_KEY") ?: error("Environment variable 'OPENAI_API_KEY' is not set")
    val client = OpenAIOkHttpClient.builder().apiKey(apiToken).build()
    val instrumentedClient = instrument(client)
    val request = ChatCompletionCreateParams.builder().addUserMessage("Generate polite greeting and introduce yourself")
        .model(ChatModel.GPT_4O_MINI).temperature(0.0).build()
    val response = instrumentedClient.chat().completions().create(request)
    val content = response.choices().first().message().content().get()
    println("Result: $content\nSee trace details in the console.")
    // Manual flush - alternatively, configure automatic flushing via ExporterCommonSettings
    TracingManager.flushTraces()
}