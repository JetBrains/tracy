/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.examples.clients.openai

import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.models.ChatModel
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.chat.completions.ChatCompletionStreamOptions
import org.jetbrains.ai.tracy.core.TracingManager
import org.jetbrains.ai.tracy.core.configureOpenTelemetrySdk
import org.jetbrains.ai.tracy.core.exporters.ConsoleExporterConfig
import org.jetbrains.ai.tracy.openai.clients.instrument

/**
 * Example of streaming with the OpenAI Chat Completions API [OpenAIClient] with tracing.
 *
 * This example demonstrates how to:
 * - Initialize tracing using [TracingManager] with [ConsoleExporterConfig].
 * - Instrument the OpenAI client using [instrument] to automatically capture trace data.
 * - Stream a Chat Completions response with tracing information automatically collected.
 * - For manual control, call [TracingManager.flushTraces] to ensure all trace data is exported immediately.
 *
 * To run this example:
 * * Set the `OPENAI_API_KEY` environment variable to your OpenAI API key.
 *
 * Run the example. Request and response spans will appear in the console output.
 */
fun main() {
    TracingManager.isTracingEnabled = true
    TracingManager.setSdk(configureOpenTelemetrySdk(ConsoleExporterConfig()))
    TracingManager.traceSensitiveContent()

    val apiToken = System.getenv("OPENAI_API_KEY") ?: error("Environment variable 'OPENAI_API_KEY' is not set")

    val instrumentedClient = OpenAIOkHttpClient.builder()
        .apiKey(apiToken)
        .build()
        .apply { instrument(this) }

    val params = ChatCompletionCreateParams.builder()
        .addUserMessage("Write a story about tracing.")
        .model(ChatModel.GPT_4O_MINI)
        .temperature(0.7)
        .streamOptions(
            ChatCompletionStreamOptions.builder()
                .includeUsage(true)
                .build()
        )
        .build()

    instrumentedClient.chat().completions().createStreaming(params).use { stream ->
        stream.stream().forEach { chunk ->
            chunk.choices().forEach { choice ->
                choice.delta().content().ifPresent { parts ->
                    parts.forEach { print(it) }
                }
            }
        }
    }
    println()

    // Manual flush - alternatively, configure automatic flushing via ExporterCommonSettings
    TracingManager.flushTraces()
}
