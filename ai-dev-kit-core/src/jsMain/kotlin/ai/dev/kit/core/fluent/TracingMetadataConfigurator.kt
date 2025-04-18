package ai.dev.kit.core.fluent

import ai.dev.kit.core.fluent.handlers.PlatformMethod
import ai.dev.kit.core.fluent.processor.Span
import ai.dev.kit.core.fluent.processor.SpanBuilder

actual fun configureTracingMetadata(
    spanBuilder: SpanBuilder,
    traceAnnotation: KotlinFlowTrace,
    method: PlatformMethod,
    args: Array<Any?>,
    client: KotlinLoggingClient
){
    TODO("Not yet implemented")
}

actual fun addOutputAttributesToTracing(span: Span, traceAnnotation: KotlinFlowTrace, result: Any?){
    TODO("Not yet implemented")
}