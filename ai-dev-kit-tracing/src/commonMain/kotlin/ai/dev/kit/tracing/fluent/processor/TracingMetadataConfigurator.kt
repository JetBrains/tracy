package ai.dev.kit.tracing.fluent.processor

import ai.dev.kit.tracing.fluent.KotlinFlowTrace
import ai.dev.kit.tracing.fluent.handlers.PlatformMethod

expect interface SpanData
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

    fun createTraceInfo(spanBuilder: SpanBuilder, method: PlatformMethod, spanName: String): Span
}

