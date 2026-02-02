package ai.jetbrains.tracy.core

import ai.jetbrains.tracy.core.fluent.FluentSpanAttributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import kotlin.coroutines.CoroutineContext

fun Span.addExceptionAttributes(exception: Throwable) {
    this.recordException(exception)
    this.setStatus(StatusCode.ERROR, exception.message ?: "Unknown error")
}

/**
 * Adds a list of Langfuse trace tags to the current active span within an OpenTelemetry trace.
 *
 * @param tags A list of tag strings to attach to the current Langfuse trace.
 * @param coroutineContext Optional coroutine context used to resolve the OpenTelemetry context.
 *                         If `null`, the current active context is used.
 */
fun addLangfuseTagsToCurrentTrace(tags: List<String>, coroutineContext: CoroutineContext? = null) {
    val otelContext = currentSpanContext(coroutineContext)
    Span.fromContext(otelContext).setAttribute(FluentSpanAttributes.LANGFUSE_TRACE_TAGS.key, tags.toString())
}
