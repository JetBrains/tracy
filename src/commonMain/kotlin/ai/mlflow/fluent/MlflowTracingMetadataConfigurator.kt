package ai.mlflow.fluent

import ai.core.fluent.KotlinFlowTrace
import ai.core.fluent.handlers.PlatformMethod
import ai.core.fluent.processor.Span
import ai.core.fluent.processor.SpanBuilder
import ai.core.fluent.processor.TracingMetadataConfigurator

expect object MlflowTracingMetadataConfigurator : TracingMetadataConfigurator {
    override fun configureMetadata(
        spanBuilder: SpanBuilder,
        traceAnnotation: KotlinFlowTrace,
        method: PlatformMethod,
        args: Array<Any?>
    )

    override fun addOutputAttribute(
        span: Span,
        traceAnnotation: KotlinFlowTrace,
        result: Any?
    )

    override fun createTraceInfo(
        spanBuilder: SpanBuilder,
        method: PlatformMethod,
        spanName: String
    )
}
