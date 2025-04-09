package ai.mlflow.fluent

import ai.core.fluent.KotlinFlowTrace
import ai.core.fluent.handlers.PlatformMethod
import ai.core.fluent.processor.Span
import ai.core.fluent.processor.SpanBuilder
import ai.core.fluent.processor.TracingMetadataConfigurator


actual object MlflowTracingMetadataConfigurator : TracingMetadataConfigurator {
    actual override fun configureMetadata(
        spanBuilder: SpanBuilder,
        traceAnnotation: KotlinFlowTrace,
        method: PlatformMethod,
        args: Array<Any?>,
    ): Unit
        = throw UnsupportedOperationException("Not yet implemented")

    actual override fun addOutputAttribute(span: Span, traceAnnotation: KotlinFlowTrace, result: Any?): Unit
        = throw UnsupportedOperationException("Not yet implemented")

    actual override fun createTraceInfo(spanBuilder: SpanBuilder, method: PlatformMethod, spanName: String): Unit
        = throw UnsupportedOperationException("Not yet implemented")
}
