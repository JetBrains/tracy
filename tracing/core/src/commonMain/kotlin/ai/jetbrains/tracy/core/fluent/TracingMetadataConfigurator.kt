package ai.jetbrains.tracy.core.fluent

import ai.jetbrains.tracy.core.fluent.handlers.PlatformMethod
import ai.jetbrains.tracy.core.fluent.processor.Span
import ai.jetbrains.tracy.core.fluent.processor.SpanBuilder

expect fun configureTracingMetadata(
    spanBuilder: SpanBuilder,
    traceAnnotation: KotlinFlowTrace,
    method: PlatformMethod,
    args: Array<Any?>
)


expect fun addOutputAttributesToTracing(span: Span, traceAnnotation: KotlinFlowTrace, result: Any?)
