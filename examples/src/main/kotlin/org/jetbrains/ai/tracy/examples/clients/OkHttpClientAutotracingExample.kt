/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.examples.clients

import org.jetbrains.ai.tracy.anthropic.adapters.AnthropicLLMTracingAdapter
import org.jetbrains.ai.tracy.core.OpenTelemetryOkHttpInterceptor
import org.jetbrains.ai.tracy.core.TracingManager
import org.jetbrains.ai.tracy.core.configureOpenTelemetrySdk
import org.jetbrains.ai.tracy.core.exporters.ConsoleExporterConfig
import org.jetbrains.ai.tracy.core.instrument
import org.jetbrains.ai.tracy.gemini.adapters.GeminiLLMTracingAdapter
import org.jetbrains.ai.tracy.openai.adapters.OpenAILLMTracingAdapter
import com.openai.models.ChatModel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Example of [OkHttpClient] instrumentation that enables OpenAI API requests tracing.
 *
 * This example demonstrates how to:
 * - Initialize tracing using [TracingManager] with [ConsoleExporterConfig].
 * - Instrument a [OkHttpClient] using [OpenTelemetryOkHttpInterceptor] to automatically capture trace data.
 * - Perform an OpenAI API request with trace data automatically captured.
 * - Traces are automatically flushed based on [ExporterCommonSettings][org.jetbrains.ai.tracy.core.exporters.ExporterCommonSettings]
 *   (periodically via `flushIntervalMs`/`flushThreshold`, and on shutdown if `flushOnShutdown = true`).
 * - For manual control, call [TracingManager.flushTraces] to ensure all trace data is exported immediately.
 *
 * To run this example:
 * * Set the `OPENAI_API_KEY` environment variable to your OpenAI API key.
 *
 * Run the example. Span will appear in the console output.
 *
 * Note: Tracy provides multiple provider-specific tracing loggers,
 * including [OpenAILLMTracingAdapter], [GeminiLLMTracingAdapter], and [AnthropicLLMTracingAdapter].
 * Choose the adapter that matches the provider your client uses.
 */
fun main() {
    TracingManager.isTracingEnabled = true
    TracingManager.setSdk(configureOpenTelemetrySdk(ConsoleExporterConfig()))
    TracingManager.traceSensitiveContent()

    val apiToken = System.getenv("OPENAI_API_KEY") ?: error("Environment variable 'OPENAI_API_KEY' is not set")

    val requestBodyJson = buildJsonObject {
        put("model", JsonPrimitive(ChatModel.GPT_4O_MINI.asString()))
        put("messages", buildJsonArray {
            add(buildJsonObject {
                put("role", JsonPrimitive("user"))
                put("content", JsonPrimitive("Generate polite greeting and introduce yourself"))
            })
        })
        put("temperature", JsonPrimitive(1.0))
    }

    val client = OkHttpClient()
    val instrumentedClient = instrument(client, OpenAILLMTracingAdapter())

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
    // Manual flush - alternatively, configure automatic flushing via ExporterCommonSettings
    TracingManager.flushTraces()
}
