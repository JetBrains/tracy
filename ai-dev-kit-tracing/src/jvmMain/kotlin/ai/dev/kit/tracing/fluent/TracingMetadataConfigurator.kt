package ai.dev.kit.tracing.fluent

import ai.dev.kit.tracing.fluent.handlers.PlatformMethod
import ai.dev.kit.tracing.fluent.processor.Span
import ai.dev.kit.tracing.fluent.processor.SpanBuilder

actual fun configureTracingMetadata(
    spanBuilder: SpanBuilder,
    traceAnnotation: KotlinFlowTrace,
    method: PlatformMethod,
    args: Array<Any?>,
) {
    val handler = traceAnnotation.attributeHandler.objectInstance
        ?: error("Handler must be an object singleton")

    with(spanBuilder) {
        setAttribute(
            FluentSpanAttributes.SPAN_INPUTS.key,
            handler.processInput(method, args)
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
    val handler = traceAnnotation.attributeHandler.objectInstance
        ?: error("Handler must be an object singleton")

    span.setAttribute(
        FluentSpanAttributes.SPAN_OUTPUTS.key,
        handler.processOutput(result)
    )
}