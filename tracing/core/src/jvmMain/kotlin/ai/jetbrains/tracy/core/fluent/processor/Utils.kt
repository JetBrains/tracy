package ai.jetbrains.tracy.core.fluent.processor

import ai.jetbrains.tracy.core.fluent.Trace
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import io.opentelemetry.extension.kotlin.getOpenTelemetryContext
import kotlin.coroutines.CoroutineContext

/**
 * Returns the active OpenTelemetry [Context] from the given [CoroutineContext].
 * Falls back to [Context.current] when no trace is attached or context is root.
 */
fun currentSpanContext(coroutineContext: CoroutineContext? = null): Context {
    val ctx = coroutineContext?.getOpenTelemetryContext() ?: return Context.current()
    return if (ctx == Context.root()) Context.current() else ctx
}

/**
 * Wraps the current OpenTelemetry [Context] as a coroutine [CoroutineContext].
 * Use this to preserve trace context across coroutines.
 */
fun currentSpanContextElement(coroutineContext: CoroutineContext? = null) =
    currentSpanContext(coroutineContext).asContextElement()

fun Trace.getSpanMetadataCustomizer() =
    this.metadataCustomizer.objectInstance ?: error("Handler must be an object singleton")

fun Span.addExceptionAttributes(exception: Throwable) {
    this.recordException(exception)
    this.setStatus(StatusCode.ERROR, exception.message ?: "Unknown error")
}
