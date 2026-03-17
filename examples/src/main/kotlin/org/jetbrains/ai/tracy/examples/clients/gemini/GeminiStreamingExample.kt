/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.examples.clients.gemini

import com.google.genai.Client
import com.google.genai.types.GenerateContentConfig
import org.jetbrains.ai.tracy.core.TracingManager
import org.jetbrains.ai.tracy.core.configureOpenTelemetrySdk
import org.jetbrains.ai.tracy.core.exporters.ConsoleExporterConfig
import org.jetbrains.ai.tracy.gemini.clients.instrument

/**
 * Example of streaming with the Google Gemini API [Client] with tracing.
 *
 * This example demonstrates how to:
 * - Initialize tracing using [TracingManager] with [ConsoleExporterConfig].
 * - Instrument the Gemini client using [instrument] to automatically capture trace data.
 * - Stream a Gemini response with tracing information automatically collected.
 * - For manual control, call [TracingManager.flushTraces] to ensure all trace data is exported immediately.
 *
 * To run this example:
 * * Set the `GEMINI_API_KEY` environment variable to your Gemini API key.
 *
 * Run the example. Request and response spans will appear in the console output.
 */
fun main() {
    TracingManager.isTracingEnabled = true
    TracingManager.setSdk(configureOpenTelemetrySdk(ConsoleExporterConfig()))
    TracingManager.traceSensitiveContent()

    val apiToken = System.getenv("GEMINI_API_KEY") ?: error("Environment variable 'GEMINI_API_KEY' is not set")

    val instrumentedClient = Client.builder()
        .apiKey(apiToken)
        .build()
        .apply { instrument(this) }

    instrumentedClient.models.generateContentStream(
        "gemini-2.5-flash",
        "Write a story about tracing.",
        GenerateContentConfig.builder()
            .temperature(0.7f)
            .build()
    ).use { stream ->
        for (chunk in stream) {
            chunk.text()?.let { print(it) }
        }
    }
    println()

    // Manual flush - alternatively, configure automatic flushing via ExporterCommonSettings
    TracingManager.flushTraces()
}
