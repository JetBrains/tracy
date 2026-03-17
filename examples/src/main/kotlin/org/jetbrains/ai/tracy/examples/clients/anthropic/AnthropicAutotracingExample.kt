/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.examples.clients.anthropic

import org.jetbrains.ai.tracy.anthropic.clients.instrument
import org.jetbrains.ai.tracy.core.TracingManager
import org.jetbrains.ai.tracy.core.configureOpenTelemetrySdk
import org.jetbrains.ai.tracy.core.exporters.ConsoleExporterConfig
import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.Model

/**
 * Example of integrating the Anthropic API client [AnthropicClient] with tracing.
 *
 * This example demonstrates how to:
 * - Initialize tracing using [TracingManager] with [ConsoleExporterConfig].
 * - Instrument the Anthropic client using [instrument] to automatically capture trace data.
 * - Perform an Anthropic API request with trace data automatically captured.
 * - Traces are automatically flushed based on [ExporterCommonSettings][org.jetbrains.ai.tracy.core.exporters.ExporterCommonSettings]
 *   (periodically via `flushIntervalMs`/`flushThreshold`, and on shutdown if `flushOnShutdown = true`).
 * - For manual control, call [TracingManager.flushTraces] to ensure all trace data is exported immediately.
 *
 * To run this example:
 * * Set the `ANTHROPIC_API_KEY` environment variable to your Anthropic API key.
 *
 * Run the example. Span will appear in the console output.
 */
fun main() {
    TracingManager.isTracingEnabled = true
    TracingManager.setSdk(configureOpenTelemetrySdk(ConsoleExporterConfig()))
    TracingManager.traceSensitiveContent()

    val apiToken = System.getenv("ANTHROPIC_API_KEY") ?: error("Environment variable 'ANTHROPIC_API_KEY' is not set")

    val instrumentedClient = AnthropicOkHttpClient.builder()
        .apiKey(apiToken)
        .build()
        .apply { instrument(this) }

    val params = MessageCreateParams.builder()
        .addUserMessage("Generate polite greeting and introduce yourself")
        .temperature(0.0)
        .maxTokens(1000L)
        .model(Model.CLAUDE_OPUS_4_0)
        .build()

    val result = instrumentedClient.messages().create(params).content().first().text().get().text()

    println("Result: $result\nSee trace details in the console.")
    // Manual flush - alternatively, configure automatic flushing via ExporterCommonSettings
    TracingManager.flushTraces()
}