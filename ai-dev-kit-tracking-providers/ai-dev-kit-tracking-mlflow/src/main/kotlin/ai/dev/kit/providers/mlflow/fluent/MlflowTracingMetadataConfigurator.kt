package ai.dev.kit.providers.mlflow.fluent

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import ai.dev.kit.core.fluent.KotlinFlowTrace
import ai.dev.kit.core.fluent.handlers.PlatformMethod
import ai.dev.kit.core.fluent.processor.TracingMetadataConfigurator
import ai.dev.kit.providers.mlflow.KotlinMlflowClient
import ai.dev.kit.providers.mlflow.createTrace
import ai.dev.kit.providers.mlflow.dataclasses.TraceInfo
import ai.dev.kit.providers.mlflow.dataclasses.createTracePostRequest

object MlflowTracingMetadataConfigurator : TracingMetadataConfigurator {
    override fun configureMetadata(
        spanBuilder: SpanBuilder,
        traceAnnotation: KotlinFlowTrace,
        method: PlatformMethod,
        args: Array<Any?>,
    ) {
        val handler = traceAnnotation.attributeHandler.objectInstance
            ?: throw IllegalStateException("Handler must be an object singleton")


        KotlinMlflowClient.currentRunId?.let {
            spanBuilder.setAttribute(MlflowFluentSpanAttributes.MLFLOW_SOURCE_RUN.asAttributeKey(), it)
        }
        spanBuilder.setAttribute(
            MlflowFluentSpanAttributes.MLFLOW_SPAN_INPUTS.asAttributeKey(),
            handler.processInput(method, args)
        )
        spanBuilder.setAttribute(
            MlflowFluentSpanAttributes.MLFLOW_SPAN_SOURCE_NAME.asAttributeKey(), method.declaringClass.name
        )
        spanBuilder.setAttribute(
            MlflowFluentSpanAttributes.MLFLOW_SPAN_TYPE.asAttributeKey(), traceAnnotation.spanType
        )
        spanBuilder.setAttribute(
            MlflowFluentSpanAttributes.MLFLOW_SPAN_FUNCTION_NAME.asAttributeKey(), method.name
        )
    }

    override fun addOutputAttribute(
        span: Span, traceAnnotation: KotlinFlowTrace, result: Any?
    ) {
        val handler = traceAnnotation.attributeHandler.objectInstance
            ?: throw IllegalStateException("Handler must be an object singleton")

        span.setAttribute(
            MlflowFluentSpanAttributes.MLFLOW_SPAN_OUTPUTS.asAttributeKey(),
            handler.processOutput(result)
        )
    }

    override fun createTraceInfo(spanBuilder: SpanBuilder, method: PlatformMethod, spanName: String) = runBlocking {
        val tracePostRequest = createTracePostRequest(
            experimentId = KotlinMlflowClient.currentExperimentId,
            runId = KotlinMlflowClient.currentRunId,
            traceCreationPath = method.declaringClass.name,
            traceName = spanName
        )
        val jsonTraceInfo = Json.encodeToString(TraceInfo.serializer(), createTrace(tracePostRequest))
        spanBuilder.setAttribute(MlflowFluentSpanAttributes.TRACE_CREATION_INFO.asAttributeKey(), jsonTraceInfo)
        return@runBlocking
    }
}
