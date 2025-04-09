package ai.core.fluent.processor

import ai.core.fluent.KotlinFlowTrace
import ai.core.fluent.handlers.PlatformMethod

expect interface SpanBuilder
expect interface Span

interface TracingMetadataConfigurator {
    fun configureMetadata(
        spanBuilder: SpanBuilder,
        traceAnnotation: KotlinFlowTrace,
        method: PlatformMethod,
        args: Array<Any?>,
    )

    fun addOutputAttribute(span: Span, traceAnnotation: KotlinFlowTrace, result: Any?)

    fun createTraceInfo(spanBuilder: SpanBuilder, method: PlatformMethod, spanName: String)
}

