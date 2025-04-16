package ai.dev.kit.core.fluent.processor

import ai.dev.kit.core.fluent.KotlinFlowTrace
import ai.dev.kit.core.fluent.handlers.PlatformMethod

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

