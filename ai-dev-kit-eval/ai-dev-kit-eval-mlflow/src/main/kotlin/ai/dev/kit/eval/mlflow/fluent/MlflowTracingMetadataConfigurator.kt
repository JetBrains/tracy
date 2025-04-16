package ai.dev.kit.eval.mlflow.fluent

import ai.dev.kit.core.fluent.KotlinFlowTrace
import ai.dev.kit.core.fluent.handlers.PlatformMethod
import ai.dev.kit.core.fluent.processor.TracingMetadataConfigurator
import ai.dev.kit.eval.base.dataclasses.TraceInfo
import ai.dev.kit.eval.base.fluent.FluentSpanAttributes
import ai.dev.kit.eval.base.fluent.addOutputAttributesToTracing
import ai.dev.kit.eval.base.fluent.configureTracingMetadata
import ai.dev.kit.eval.mlflow.KotlinMlflowClient
import ai.dev.kit.eval.mlflow.createTrace
import ai.dev.kit.eval.mlflow.dataclasses.createTracePostRequest
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

object MlflowTracingMetadataConfigurator : TracingMetadataConfigurator {
    override fun configureMetadata(
        spanBuilder: SpanBuilder,
        traceAnnotation: KotlinFlowTrace,
        method: PlatformMethod,
        args: Array<Any?>,
    ) {
        configureTracingMetadata(
            spanBuilder,
            traceAnnotation,
            method,
            args,
            KotlinMlflowClient
        )
    }

    override fun addOutputAttribute(
        span: Span, traceAnnotation: KotlinFlowTrace, result: Any?
    ) {
        addOutputAttributesToTracing(span, traceAnnotation, result)
    }

    override fun createTraceInfo(
        spanBuilder: SpanBuilder, method: PlatformMethod, spanName: String
    ): Span = runBlocking {
        val tracePostRequest = createTracePostRequest(
            experimentId = KotlinMlflowClient.currentExperimentId,
            runId = KotlinMlflowClient.currentRunId,
            traceCreationPath = method.declaringClass.name,
            traceName = spanName
        )
        val jsonTraceInfo = Json.encodeToString(TraceInfo.serializer(), createTrace(tracePostRequest))
        spanBuilder.setAttribute(FluentSpanAttributes.TRACE_CREATION_INFO.asAttributeKey(), jsonTraceInfo)

        return@runBlocking spanBuilder.startSpan()
    }
}
