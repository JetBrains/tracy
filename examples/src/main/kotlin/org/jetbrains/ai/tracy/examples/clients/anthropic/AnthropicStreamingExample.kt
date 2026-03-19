/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.examples.clients.anthropic

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.Model
import org.jetbrains.ai.tracy.anthropic.clients.instrument
import org.jetbrains.ai.tracy.core.TracingManager
import org.jetbrains.ai.tracy.core.configureOpenTelemetrySdk
import org.jetbrains.ai.tracy.core.exporters.ConsoleExporterConfig

/**
 * Example of streaming with the Anthropic API [AnthropicClient] with tracing.
 *
 * This example demonstrates how to:
 * - Initialize tracing using [TracingManager] with [ConsoleExporterConfig].
 * - Instrument the Anthropic client using [instrument] to automatically capture trace data.
 * - Stream an Anthropic response with tracing information automatically collected.
 * - For manual control, call [TracingManager.flushTraces] to ensure all trace data is exported immediately.
 *
 * To run this example:
 * * Set the `ANTHROPIC_API_KEY` environment variable to your Anthropic API key.
 *
 * Run the example. Request and response spans will appear in the console output.
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
        .addUserMessage("Write a story about tracing.")
        .maxTokens(1000L)
        .temperature(0.7)
        .model(Model.CLAUDE_SONNET_4_5)
        .build()

    instrumentedClient.messages().createStreaming(params).use { stream ->
        stream.stream().forEach { event ->
            event.contentBlockDelta().ifPresent { blockDelta ->
                blockDelta.delta().text().ifPresent { print(it.text()) }
            }
        }
    }
    println()

    // Manual flush - alternatively, configure automatic flushing via ExporterCommonSettings
    TracingManager.flushTraces()
}
