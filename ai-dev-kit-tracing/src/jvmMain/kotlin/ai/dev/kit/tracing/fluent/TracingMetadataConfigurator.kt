package ai.dev.kit.tracing.fluent

import ai.dev.kit.tracing.fluent.handlers.PlatformMethod
import ai.dev.kit.tracing.fluent.processor.Span
import ai.dev.kit.tracing.fluent.processor.SpanBuilder
import ai.dev.kit.tracing.fluent.processor.getSpanAttributeHandler

actual fun configureTracingMetadata(
    spanBuilder: SpanBuilder,
    traceAnnotation: KotlinFlowTrace,
    method: PlatformMethod,
    args: Array<Any?>,
) {
    with(spanBuilder) {
        setAttribute(
            FluentSpanAttributes.SPAN_INPUTS.key,
            traceAnnotation.getSpanAttributeHandler().formatInputAttributes(method, args)
        )
        setAttribute(
            FluentSpanAttributes.SPAN_SOURCE_NAME.key, method.declaringClass.name
        )
        setAttribute(
            FluentSpanAttributes.SPAN_TYPE.key, traceAnnotation.spanType
        )
        setAttribute(
            FluentSpanAttributes.SPAN_FUNCTION_NAME.key, method.name
        )
    }
}


actual fun addOutputAttributesToTracing(span: Span, traceAnnotation: KotlinFlowTrace, result: Any?) {
    span.setAttribute(
        FluentSpanAttributes.SPAN_OUTPUTS.key,
        traceAnnotation.getSpanAttributeHandler().formatOutputAttribute(result)
    )
}