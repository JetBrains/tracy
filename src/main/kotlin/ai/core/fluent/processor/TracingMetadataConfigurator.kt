package ai.core.fluent.processor

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import ai.core.fluent.KotlinFlowTrace
import java.lang.reflect.Method

interface TracingMetadataConfigurator {
    fun configureMetadata(
        spanBuilder: SpanBuilder,
        traceAnnotation: KotlinFlowTrace,
        method: Method,
        args: Array<Any?>,
    )

    fun addOutputAttribute(span: Span, traceAnnotation: KotlinFlowTrace, result: Any?)

    fun createTraceInfo(spanBuilder: SpanBuilder, method: Method, spanName: String)
}
