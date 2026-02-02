package ai.jetbrains.tracy.core.fluent

/**
 * Defines standard attribute keys used for Tracy tracing spans.
 *
 * These attributes are attached to OpenTelemetry spans to record
 * function inputs, outputs, and execution metadata.
 */
enum class FluentSpanAttributes(val key: String) {
    SPAN_INPUTS("input"),
    SPAN_OUTPUTS("output"),
    SESSION_ID("session.id"),
    CODE_FUNCTION_NAME("code.function.name"),
    LANGFUSE_TRACE_TAGS("langfuse.trace.tags");
}