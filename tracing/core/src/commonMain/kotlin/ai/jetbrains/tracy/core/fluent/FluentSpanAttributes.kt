package ai.jetbrains.tracy.core.fluent

enum class FluentSpanAttributes(val key: String) {
    SPAN_INPUTS("input"),
    SPAN_OUTPUTS("output"),
    SESSION_ID("session.id"),
    CODE_FUNCTION_NAME("code.function.name"),
    LANGFUSE_TRACE_TAGS("langfuse.trace.tags");
}