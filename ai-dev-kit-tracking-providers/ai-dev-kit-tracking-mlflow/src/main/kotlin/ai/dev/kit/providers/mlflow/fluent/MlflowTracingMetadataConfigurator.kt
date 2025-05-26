package ai.dev.kit.providers.mlflow.fluent

import ai.dev.kit.eval.utils.createTracePostRequest
import ai.dev.kit.providers.mlflow.createTrace
import ai.dev.kit.tracing.fluent.FluentSpanAttributes
import ai.dev.kit.tracing.fluent.KotlinFlowTrace
import ai.dev.kit.tracing.fluent.TracingSessionProvider
import ai.dev.kit.tracing.fluent.addOutputAttributesToTracing
import ai.dev.kit.tracing.fluent.dataclasses.TraceInfo
import ai.dev.kit.tracing.fluent.handlers.PlatformMethod
import ai.dev.kit.tracing.fluent.processor.TracingMetadataConfigurator
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

class MlflowTracingMetadataConfigurator : TracingMetadataConfigurator {
    override fun addOutputAttribute(
        span: Span, traceAnnotation: KotlinFlowTrace, result: Any?
    ) {
        addOutputAttributesToTracing(span, traceAnnotation, result)
    }

    override fun createTraceInfo(
        spanBuilder: SpanBuilder, method: PlatformMethod, spanName: String
    ): Span = runBlocking {
        val experimentId = TracingSessionProvider.currentProjectId
            ?: error("For MLFlow, you must obtain an experimentId and set it using `withExperimentId(experimentId) {...}`")

        val tracePostRequest = createTracePostRequest(
            experimentId = experimentId,
            runId = TracingSessionProvider.currentSessionId,
            traceCreationPath = method.declaringClass.name,
            traceName = spanName
        )
        val jsonTraceInfo = Json.encodeToString(TraceInfo.serializer(), createTrace(tracePostRequest))
        spanBuilder.setAttribute(FluentSpanAttributes.TRACE_CREATION_INFO.key, jsonTraceInfo)

        spanBuilder.startSpan()
    }
}
