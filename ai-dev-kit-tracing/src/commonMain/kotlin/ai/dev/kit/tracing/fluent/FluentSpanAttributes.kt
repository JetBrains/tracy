package ai.dev.kit.tracing.fluent

import ai.dev.kit.tracing.fluent.processor.SpanData

enum class FluentSpanAttributes(val key: String) {
    SPAN_INPUTS("spanInputs"),
    SPAN_OUTPUTS("spanOutputs"),
    SOURCE_RUN("sourceRun"),
    SPAN_FUNCTION_NAME("spanFunctionName"),
    SPAN_SOURCE_NAME("source.name"),
    SPAN_TYPE("spanType"),
    TRACE_CREATION_INFO("traceCreationInfo");
}

expect fun SpanData.getAttribute(spanAttributeKey: FluentSpanAttributes): String?