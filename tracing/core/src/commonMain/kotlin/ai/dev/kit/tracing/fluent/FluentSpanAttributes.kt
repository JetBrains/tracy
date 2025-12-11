package ai.dev.kit.tracing.fluent

import ai.dev.kit.tracing.fluent.processor.SpanData

enum class FluentSpanAttributes(val key: String) {
    SPAN_INPUTS("input"),
    SPAN_OUTPUTS("output"),
    SESSION_ID("session.id"),
    CODE_FUNCTION_NAME("code.function.name"),
    LANGFUSE_TRACE_TAGS("langfuse.trace.tags");
}

expect fun SpanData.getAttribute(spanAttributeKey: FluentSpanAttributes): String?