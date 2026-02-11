/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package ai.jetbrains.tracy.examples.clients

import ai.jetbrains.tracy.anthropic.adapters.AnthropicLLMTracingAdapter
import ai.jetbrains.tracy.core.TracingManager
import ai.jetbrains.tracy.core.configureOpenTelemetrySdk
import ai.jetbrains.tracy.core.exporters.ConsoleExporterConfig
import ai.jetbrains.tracy.gemini.adapters.GeminiLLMTracingAdapter
import ai.jetbrains.tracy.ktor.instrument
import ai.jetbrains.tracy.openai.adapters.OpenAILLMTracingAdapter
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/**
 * Example of integrating a Ktor [HttpClient] with tracing for OpenAI API requests.
 *
 * This example demonstrates how to:
 * - Initialize tracing using [TracingManager] with [ConsoleExporterConfig].
 * - Instrument a Ktor [HttpClient] using [OpenAILLMTracingAdapter] to automatically capture trace data.
 * - Perform an OpenAI API request with trace data automatically captured.
 * - Traces are automatically flushed based on [ExporterCommonSettings][ai.jetbrains.tracy.core.exporters.ExporterCommonSettings]
 *   (periodically via `flushIntervalMs`/`flushThreshold`, and on shutdown if `flushOnShutdown = true`).
 * - For manual control, call [TracingManager.flushTraces] to ensure all trace data is exported immediately.
 *
 * To run this example:
 * * Set the `OPENAI_API_KEY` environment variable to your OpenAI API key.
 *
 * Run the example. Span will appear in the console output.
 *
 * Note: Tracy provides multiple provider-specific tracing adapters,
 * including [OpenAILLMTracingAdapter], [GeminiLLMTracingAdapter], and [AnthropicLLMTracingAdapter].
 * Choose the adapter that matches the provider your client uses.
 */
suspend fun main() {
    TracingManager.setSdk(configureOpenTelemetrySdk(ConsoleExporterConfig()))
    TracingManager.traceSensitiveContent()

    val apiToken = System.getenv("OPENAI_API_KEY") ?: error("Environment variable 'OPENAI_API_KEY' is not set")
    val client = HttpClient {
        install(ContentNegotiation) {
            json(Json { prettyPrint = true })
        }
    }
    val instrumentedClient = instrument(client, OpenAILLMTracingAdapter())
    val requestBody = buildJsonObject {
        put("model", JsonPrimitive("gpt-4o-mini"))
        put("messages", buildJsonArray {
            add(buildJsonObject {
                put("role", JsonPrimitive("user"))
                put("content", JsonPrimitive("Generate polite greeting and introduce yourself"))
            })
        })
        put("temperature", JsonPrimitive(1.0))
    }
    val response = instrumentedClient.post("https://api.openai.com/v1/chat/completions") {
        header(HttpHeaders.Authorization, "Bearer $apiToken")
        contentType(ContentType.Application.Json)
        setBody(requestBody)
    }
    println("Result: ${response.bodyAsText()}\nSee trace details in the console.")
    // Manual flush - alternatively, configure automatic flushing via ExporterCommonSettings
    TracingManager.flushTraces()
}
