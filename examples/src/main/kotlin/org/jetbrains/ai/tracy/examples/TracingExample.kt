/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.examples

import org.jetbrains.ai.tracy.core.TracingManager
import org.jetbrains.ai.tracy.core.configureOpenTelemetrySdk
import org.jetbrains.ai.tracy.core.exporters.ConsoleExporterConfig
import org.jetbrains.ai.tracy.core.instrumentation.Trace

@Trace(name = "SimpleExample")
private fun printName(name: String): String {
    println("Hello $name!")
    return "Name successfully printed"
}

/**
 * Demonstrates basic function tracing using Tracy.
 *
 * This example shows how:
 * - Initializing tracing with [TracingManager] and [ConsoleExporterConfig].
 * - Annotating a function with [Trace] to generate spans automatically.
 * - Traces are automatically flushed based on [ExporterCommonSettings][org.jetbrains.ai.tracy.core.exporters.ExporterCommonSettings]
 *   (periodically via `flushIntervalMs`/`flushThreshold`, and on shutdown if `flushOnShutdown = true`).
 * - For manual control, call [TracingManager.flushTraces] to ensure all trace data is exported immediately.
 *
 * Running this example creates a single span named **SimpleExample**
 * representing the execution of the [printName] function.
 */
fun main() {
    TracingManager.isTracingEnabled = true
    TracingManager.setSdk(configureOpenTelemetrySdk(ConsoleExporterConfig()))
    printName("Bob")
    println("See trace details in the console.")
    // Manual flush - alternatively, configure automatic flushing via ExporterCommonSettings
    TracingManager.flushTraces()
}