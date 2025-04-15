package ai.dev.kit.eval.base.fluent

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.trace.data.SpanData

enum class FluentSpanAttributes(val key: String) {
    SPAN_INPUTS("spanInputs"),
    SPAN_OUTPUTS("spanOutputs"),
    SOURCE_RUN("sourceRun"),
    SPAN_FUNCTION_NAME("spanFunctionName"),
    SPAN_SOURCE_NAME("source.name"),
    SPAN_TYPE("spanType"),
    TRACE_CREATION_INFO("traceCreationInfo");

    fun asAttributeKey(): AttributeKey<String> = AttributeKey.stringKey(key)
}

fun SpanData.getAttribute(spanAttributeKey: FluentSpanAttributes) =
    this.attributes[spanAttributeKey.asAttributeKey()]