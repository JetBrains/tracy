package ai.jetbrains.tracy.core.fluent

import ai.jetbrains.tracy.core.fluent.handlers.PlatformMethod
import ai.jetbrains.tracy.core.fluent.processor.Span
import ai.jetbrains.tracy.core.fluent.processor.SpanBuilder
import ai.jetbrains.tracy.core.fluent.processor.getSpanMetadataCustomizer

actual fun configureTracingMetadata(
    spanBuilder: SpanBuilder,
    traceAnnotation: KotlinFlowTrace,
    method: PlatformMethod,
    args: Array<Any?>,
) {
    with(spanBuilder) {
        setAttribute(
            FluentSpanAttributes.SPAN_INPUTS.key,
            traceAnnotation.getSpanMetadataCustomizer().formatInputAttributes(method, args)
        )
        setAttribute(
            FluentSpanAttributes.CODE_FUNCTION_NAME.key, "${method.declaringClass.name}.${method.name}"
        )
    }
}


actual fun addOutputAttributesToTracing(span: Span, traceAnnotation: KotlinFlowTrace, result: Any?) {
    span.setAttribute(
        FluentSpanAttributes.SPAN_OUTPUTS.key,
        traceAnnotation.getSpanMetadataCustomizer().formatOutputAttribute(result)
    )
}