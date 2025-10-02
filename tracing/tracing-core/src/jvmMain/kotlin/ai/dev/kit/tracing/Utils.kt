package ai.dev.kit.tracing

import ai.dev.kit.tracing.fluent.FluentSpanAttributes
import ai.dev.kit.tracing.fluent.processor.Span
import ai.dev.kit.tracing.fluent.processor.getOpenTelemetryContext
import io.opentelemetry.context.Context
import kotlin.coroutines.CoroutineContext

fun addTagsToCurrentTrace(tags: List<String>, coroutineContext: CoroutineContext? = null) {
    val otelContext = coroutineContext?.let { getOpenTelemetryContext(it) } ?: Context.current()
    Span.fromContext(otelContext).setAttribute(FluentSpanAttributes.TRACE_TAGS.key, tags.toString())
}
