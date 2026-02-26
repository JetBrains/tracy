/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.examples

import org.jetbrains.ai.tracy.core.TracingManager
import org.jetbrains.ai.tracy.core.configureOpenTelemetrySdk
import org.jetbrains.ai.tracy.core.exporters.ConsoleExporterConfig
import org.jetbrains.ai.tracy.core.instrumentation.TracySpanAttributes
import org.jetbrains.ai.tracy.core.instrumentation.processor.withSpan

fun handleUserLogin(username: String, password: String) =
    withSpan("HandleUserLogin", mapOf("username" to username)) { span ->
        println("Authenticating user: $username")
        val result = if (password == "secret123") "Login successful" else "Invalid credentials"
        span.setAttribute(TracySpanAttributes.SPAN_INPUTS.key, "username=$username")
        span.setAttribute(TracySpanAttributes.SPAN_OUTPUTS.key, result)
        println("Result: $result")
        return@withSpan result
    }

/**
 * Example of using manual tracing with [withSpan].
 *
 * This example demonstrates how to:
 * - Create custom tracing spans manually using [withSpan].
 * - Add input and output attributes to spans with [TracySpanAttributes].
 *
 * When you run this example, you will see three spans created:
 * - One parent span named **LoginAttempt**, representing the overall login process.
 * - Two child spans named **HandleUserLogin**, each corresponding to a user authentication attempt.
 */
fun main() {
    TracingManager.isTracingEnabled = true
    TracingManager.setSdk(configureOpenTelemetrySdk(ConsoleExporterConfig()))
    withSpan("LoginAttempt") {
        handleUserLogin("alice", "secret123")
        handleUserLogin("bob", "wrongpass")
    }
    println("See trace details in the console.")
    // Manual flush - alternatively, configure automatic flushing via ExporterCommonSettings
    TracingManager.flushTraces()
}
