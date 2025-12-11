package ai.dev.kit.tracing.fluent

import ai.dev.kit.tracing.fluent.processor.SpanData

enum class FluentSpanAttributes(val key: String) {
    SPAN_INPUTS("input"),
    SPAN_OUTPUTS("output"),
    SOURCE_RUN("session.id"),
    SPAN_FUNCTION_NAME("code.function.name"),
    SPAN_SOURCE_NAME("source.name"),
    SPAN_TYPE("spanType"),
    LANGFUSE_TRACE_TAGS("langfuse.trace.tags");
}

expect fun SpanData.getAttribute(spanAttributeKey: FluentSpanAttributes): String?