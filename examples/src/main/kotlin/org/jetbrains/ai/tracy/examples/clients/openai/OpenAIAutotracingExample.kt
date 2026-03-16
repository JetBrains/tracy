/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.examples.clients.openai

import org.jetbrains.ai.tracy.core.TracingManager
import org.jetbrains.ai.tracy.core.configureOpenTelemetrySdk
import org.jetbrains.ai.tracy.core.exporters.ConsoleExporterConfig
import org.jetbrains.ai.tracy.openai.clients.instrument
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
 * - Traces are automatically flushed based on [ExporterCommonSettings][org.jetbrains.ai.tracy.core.exporters.ExporterCommonSettings]
 *   (periodically via `flushIntervalMs`/`flushThreshold`, and on shutdown if `flushOnShutdown = true`).
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

    val request = ChatCompletionCreateParams.builder()
        .addUserMessage("Generate polite greeting and introduce yourself")
        .model(ChatModel.GPT_4O_MINI)
        .temperature(0.0)
        .build()

    val response = client.chat().completions().create(request)
    val content = response.choices().first().message().content().get()
    println("Result: $content\nSee trace details in the console.")
    // Manual flush - alternatively, configure automatic flushing via ExporterCommonSettings
    TracingManager.flushTraces()
}
