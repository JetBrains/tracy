/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.examples.clients.openai

import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.models.ChatModel
import com.openai.models.responses.ResponseCreateParams
import org.jetbrains.ai.tracy.core.TracingManager
import org.jetbrains.ai.tracy.core.configureOpenTelemetrySdk
import org.jetbrains.ai.tracy.core.exporters.ConsoleExporterConfig
import org.jetbrains.ai.tracy.openai.clients.instrument

/**
 * Example of streaming with the OpenAI Responses API [OpenAIClient] with tracing.
 *
 * This example demonstrates how to:
 * - Initialize tracing using [TracingManager] with [ConsoleExporterConfig].
 * - Instrument the OpenAI client using [instrument] to automatically capture trace data.
 * - Stream a Responses API response with tracing information automatically collected.
 * - For manual control, call [TracingManager.flushTraces] to ensure all trace data is exported immediately.
 *
 * To run this example:
 * * Set the `OPENAI_API_KEY` (or `LLM_PROVIDER_API_KEY`) environment variable to your OpenAI API key.
 *
 * Run the example. Request and response spans will appear in the console output.
 */
fun main() {
    TracingManager.isTracingEnabled = true
    TracingManager.setSdk(configureOpenTelemetrySdk(ConsoleExporterConfig()))
    TracingManager.traceSensitiveContent()

    val apiKey = System.getenv("OPENAI_API_KEY") ?: System.getenv("LLM_PROVIDER_API_KEY")
        ?: error("LLM_PROVIDER_API_KEY environment variable is not set")

    val client = OpenAIOkHttpClient.builder()
        .apiKey(apiKey)
        .build()
        .apply { instrument(this) }

    val params = ResponseCreateParams.builder()
        .input("Write a story about tracing.")
        .model(ChatModel.GPT_4O_MINI)
        .temperature(0.7)
        .build()

    client.responses().createStreaming(params).use { stream ->
        stream.stream().forEach { event ->
            event.outputTextDelta().ifPresent { delta ->
                print(delta.delta())
            }
        }
    }
    println()

    // Manual flush - alternatively, configure automatic flushing via ExporterCommonSettings
    TracingManager.flushTraces()
}
