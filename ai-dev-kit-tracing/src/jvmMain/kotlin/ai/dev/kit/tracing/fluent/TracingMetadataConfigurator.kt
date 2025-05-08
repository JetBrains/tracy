package ai.dev.kit.tracing.fluent

import ai.dev.kit.tracing.fluent.handlers.PlatformMethod
import ai.dev.kit.tracing.fluent.processor.Span
import ai.dev.kit.tracing.fluent.processor.SpanBuilder

actual fun configureTracingMetadata(
    spanBuilder: SpanBuilder,
    traceAnnotation: KotlinFlowTrace,
    method: PlatformMethod,
    args: Array<Any?>,
    client: KotlinLoggingClient
) {
    val handler = traceAnnotation.attributeHandler.objectInstance
        ?: throw IllegalStateException("Handler must be an object singleton")

    client.currentRunId?.let {
        spanBuilder.setAttribute(FluentSpanAttributes.SOURCE_RUN.key, it)
    }
    spanBuilder.setAttribute(
        FluentSpanAttributes.SPAN_INPUTS.key,
        handler.processInput(method, args)
    )
    spanBuilder.setAttribute(
        FluentSpanAttributes.SPAN_SOURCE_NAME.key, method.declaringClass.name
    )
    spanBuilder.setAttribute(
        FluentSpanAttributes.SPAN_TYPE.key, traceAnnotation.spanType
    )
    spanBuilder.setAttribute(
        FluentSpanAttributes.SPAN_FUNCTION_NAME.key, method.name
    )
}


actual fun addOutputAttributesToTracing(span: Span, traceAnnotation: KotlinFlowTrace, result: Any?) {
    val handler = traceAnnotation.attributeHandler.objectInstance
        ?: throw IllegalStateException("Handler must be an object singleton")

    span.setAttribute(
        FluentSpanAttributes.SPAN_OUTPUTS.key,
        handler.processOutput(result)
    )
}