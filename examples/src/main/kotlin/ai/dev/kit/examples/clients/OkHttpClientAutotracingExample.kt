package ai.dev.kit.examples.clients

import ai.dev.kit.clients.OpenTelemetryAnthropicLogger
import ai.dev.kit.clients.OpenTelemetryGeminiLogger
import ai.dev.kit.clients.OpenTelemetryOpenAILogger
import ai.dev.kit.instrument
import ai.dev.kit.tracing.ConsoleConfig
import ai.dev.kit.tracing.TracingManager
import ai.dev.kit.tracing.configureOpenTelemetrySdk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Example of integrating a OkHttp Client [OkHttpClient] with tracing for OpenAI API requests.
 *
 * This example demonstrates how to:
 * - Initialize tracing using [TracingManager] with [ConsoleConfig].
 * - Instrument a [OkHttpClient] using [OpenTelemetryOpenAILogger] to automatically capture trace data.
 * - Perform an OpenAI API request with trace data automatically captured.
 * - Call [TracingManager.flushTraces] before exiting to ensure all trace data is exported.
 *
 * To run this example:
 * * Set the `OPENAI_API_KEY` environment variable to your OpenAI API key.
 *
 * Run the example. Span will appear in the console output.
 *
 * Note: The AI Dev Kit provides multiple provider-specific tracing loggers,
 * including [OpenTelemetryOpenAILogger], [OpenTelemetryGeminiLogger], and [OpenTelemetryAnthropicLogger].
 * Choose the adapter that matches the provider your client uses.
 */
fun main() {
    TracingManager.setSdk(configureOpenTelemetrySdk(ConsoleConfig()))
    val apiToken = System.getenv("OPENAI_API_KEY")
        ?: error("Environment variable 'OPENAI_API_KEY' is not set")
    val requestBodyJson = buildJsonObject {
        put("model", JsonPrimitive("gpt-4o-mini"))
        put("messages", buildJsonArray {
            add(buildJsonObject {
                put("role", JsonPrimitive("user"))
                put("content", JsonPrimitive("Generate polite greeting and introduce yourself"))
            })
        })
        put("temperature", JsonPrimitive(1.0))
    }
    val client = OkHttpClient()
    val instrumentedClient = instrument(client, OpenTelemetryOpenAILogger())
    val requestBody = Json { prettyPrint = true }
        .encodeToString(requestBodyJson)
        .toRequestBody("application/json".toMediaType())
    val request = Request.Builder().url("https://api.openai.com/v1/chat/completions")
        .addHeader("Authorization", "Bearer $apiToken")
        .addHeader("Content-Type", "application/json")
        .post(requestBody)
        .build()
    instrumentedClient.newCall(request).execute().use { response ->
        println("Result: ${response.body?.string() ?: "<empty response>"}\nSee trace details in the console.")
    }
    TracingManager.flushTraces()
}
