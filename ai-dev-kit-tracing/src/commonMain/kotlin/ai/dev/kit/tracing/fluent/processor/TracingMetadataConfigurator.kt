package ai.dev.kit.tracing.fluent.processor

import ai.dev.kit.tracing.fluent.KotlinFlowTrace
import ai.dev.kit.tracing.fluent.addOutputAttributesToTracing
import ai.dev.kit.tracing.fluent.handlers.PlatformMethod

expect interface SpanData
expect interface SpanBuilder
expect interface Span

abstract class TracingMetadataConfigurator {
    open fun addOutputAttribute(span: Span, traceAnnotation: KotlinFlowTrace, result: Any?) =
        addOutputAttributesToTracing(span, traceAnnotation, result)
    abstract fun createTraceInfo(spanBuilder: SpanBuilder, method: PlatformMethod, spanName: String): Span
}

