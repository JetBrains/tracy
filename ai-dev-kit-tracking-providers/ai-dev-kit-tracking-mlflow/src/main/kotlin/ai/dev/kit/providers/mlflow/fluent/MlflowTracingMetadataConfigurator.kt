package ai.dev.kit.providers.mlflow.fluent

import ai.dev.kit.core.fluent.FluentSpanAttributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import ai.dev.kit.core.fluent.KotlinFlowTrace
import ai.dev.kit.core.fluent.addOutputAttributesToTracing
import ai.dev.kit.core.fluent.configureTracingMetadata
import ai.dev.kit.core.fluent.handlers.PlatformMethod
import ai.dev.kit.core.fluent.processor.TracingMetadataConfigurator
import ai.dev.kit.core.fluent.dataclasses.TraceInfo
import ai.dev.kit.providers.mlflow.KotlinMlflowClient
import ai.dev.kit.providers.mlflow.createTrace
import ai.dev.kit.providers.mlflow.dataclasses.createTracePostRequest

class MlflowTracingMetadataConfigurator : TracingMetadataConfigurator {
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
        spanBuilder.setAttribute(FluentSpanAttributes.TRACE_CREATION_INFO.key, jsonTraceInfo)

        return@runBlocking spanBuilder.startSpan()
    }
}
