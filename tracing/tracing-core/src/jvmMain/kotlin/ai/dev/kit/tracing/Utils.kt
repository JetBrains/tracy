package ai.dev.kit.tracing

import ai.dev.kit.tracing.fluent.FluentSpanAttributes
import ai.dev.kit.tracing.fluent.processor.Span
import ai.dev.kit.tracing.fluent.processor.currentSpanContext
import kotlin.coroutines.CoroutineContext

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
