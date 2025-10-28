package ai.dev.kit.examples

import ai.dev.kit.exporters.ConsoleExporterConfig
import ai.dev.kit.tracing.TracingManager
import ai.dev.kit.tracing.configureOpenTelemetrySdk
import ai.dev.kit.tracing.fluent.FluentSpanAttributes
import ai.dev.kit.tracing.fluent.processor.withSpan

fun handleUserLogin(username: String, password: String) =
    withSpan("HandleUserLogin", mapOf("username" to username)) { span ->
        println("Authenticating user: $username")
        val result = if (password == "secret123") "Login successful" else "Invalid credentials"
        span.setAttribute(FluentSpanAttributes.SPAN_INPUTS.key, "username=$username")
        span.setAttribute(FluentSpanAttributes.SPAN_OUTPUTS.key, result)
        println("Result: $result")
        return@withSpan result
    }

/**
 * Example of using manual tracing with [withSpan].
 *
 * This example demonstrates how to:
 * - Create custom tracing spans manually using [withSpan].
 * - Add input and output attributes to spans with [FluentSpanAttributes].
 *
 * When you run this example, you will see three spans created:
 * - One parent span named **LoginAttempt**, representing the overall login process.
 * - Two child spans named **HandleUserLogin**, each corresponding to a user authentication attempt.
 */
fun main() {
    TracingManager.setSdk(configureOpenTelemetrySdk(ConsoleExporterConfig()))
    withSpan("LoginAttempt") {
        handleUserLogin("alice", "secret123")
        handleUserLogin("bob", "wrongpass")
    }
    println("See trace details in the console.")
    TracingManager.flushTraces()
}
