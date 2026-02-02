package ai.jetbrains.tracy.core.fluent.processor

import ai.jetbrains.tracy.core.TracingManager
import ai.jetbrains.tracy.core.addExceptionAttributes
import io.opentelemetry.api.trace.Span

inline fun <T> withSpan(
    name: String,
    attributes: Map<String, Any?> = emptyMap(),
    block: (Span) -> T
): T {
    val tracer = TracingManager.tracer

    val span = tracer.spanBuilder(name).startSpan()
    val scope = span.makeCurrent()

    attributes.forEach { (key, value) ->
        // TODO: deal with types
        span.setAttribute(key, value.toString())
    }

    try {
        val result = block(span)
        span.setAttribute("output", result.toString())

        return result
    } catch (e: Exception) {
        span.addExceptionAttributes(e)
        throw e
    } finally {
        scope.close()
        span.end()
    }
}