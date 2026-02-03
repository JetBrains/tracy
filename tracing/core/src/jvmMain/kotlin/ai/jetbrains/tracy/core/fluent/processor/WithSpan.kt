package ai.jetbrains.tracy.core.fluent.processor

import ai.jetbrains.tracy.core.TracingManager
import ai.jetbrains.tracy.core.fluent.FluentSpanAttributes
import io.opentelemetry.api.trace.Span

/**
 * Executes the given [block] within a manually created tracing span.
 *
 * This function provides a convenient API for manual tracing, handling
 * span creation, activation, and closing (including in the presence of
 * exceptions).
 *
 * It is especially useful when annotation-based tracing is not available,
 * such as in Java code, or when fine-grained control over tracing is needed.
 *
 * @param name the name of the created span
 * @param attributes optional attributes to attach to the span
 * @param block the code to execute within the span's context
 *
 * @return the result of [block]
 */
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
        span.setAttribute(FluentSpanAttributes.SPAN_OUTPUTS.key, result.toString())

        return result
    } catch (e: Exception) {
        span.addExceptionAttributes(e)
        throw e
    } finally {
        scope.close()
        span.end()
    }
}