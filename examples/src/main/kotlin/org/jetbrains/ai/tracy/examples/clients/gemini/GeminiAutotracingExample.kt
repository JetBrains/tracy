/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.examples.clients.gemini

import org.jetbrains.ai.tracy.core.TracingManager
import org.jetbrains.ai.tracy.core.configureOpenTelemetrySdk
import org.jetbrains.ai.tracy.core.exporters.ConsoleExporterConfig
import org.jetbrains.ai.tracy.gemini.clients.instrument
import com.google.genai.Client
import com.google.genai.types.GenerateContentConfig

/**
 * Example of integrating the Google Gemini API [Client] client with tracing.
 *
 * This example demonstrates how to:
 * - Initialize tracing using [TracingManager] with [ConsoleExporterConfig].
 * - Instrument the Gemini client using [instrument] to automatically capture trace data.
 * - Perform a Gemini API request with trace data automatically captured.
 * - Traces are automatically flushed based on [ExporterCommonSettings][org.jetbrains.ai.tracy.core.exporters.ExporterCommonSettings]
 *   (periodically via `flushIntervalMs`/`flushThreshold`, and on shutdown if `flushOnShutdown = true`).
 * - For manual control, call [TracingManager.flushTraces] to ensure all trace data is exported immediately.
 *
 * To run this example:
 * * Set the `GEMINI_API_KEY` (or `LLM_PROVIDER_API_KEY`) environment variable to your Gemini API key.
 *
 * Run the example. Span will appear in the console output.
 */
fun main() {
    TracingManager.isTracingEnabled = true
    TracingManager.setSdk(configureOpenTelemetrySdk(ConsoleExporterConfig()))
    TracingManager.traceSensitiveContent()

    val apiKey = System.getenv("GEMINI_API_KEY") ?: System.getenv("LLM_PROVIDER_API_KEY")
        ?: error("LLM_PROVIDER_API_KEY environment variable is not set")

    val client = Client.builder()
        .apiKey(apiKey)
        .build()
        .apply { instrument(this) }

    val result = client.models.generateContent(
        "gemini-2.5-flash",
        "Generate polite greeting and introduce yourself",
        GenerateContentConfig.builder().temperature(0.0f).build()
    )

    println("Result: $result\nSee trace details in the console.")
    // Manual flush - alternatively, configure automatic flushing via ExporterCommonSettings
    TracingManager.flushTraces()
}
