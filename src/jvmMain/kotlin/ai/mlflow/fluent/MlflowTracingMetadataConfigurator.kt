package ai.mlflow.fluent

import ai.core.fluent.KotlinFlowTrace
import ai.core.fluent.handlers.PlatformMethod
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import ai.core.fluent.processor.TracingMetadataConfigurator
import org.example.ai.mlflow.KotlinMlflowClient
import org.example.ai.mlflow.createTrace
import org.example.ai.mlflow.dataclasses.TraceInfo
import org.example.ai.mlflow.dataclasses.createTracePostRequest

actual object MlflowTracingMetadataConfigurator : TracingMetadataConfigurator {
    actual override fun configureMetadata(
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

    actual override fun addOutputAttribute(
        span: Span, traceAnnotation: KotlinFlowTrace, result: Any?
    ) {
        val handler = traceAnnotation.attributeHandler.objectInstance
            ?: throw IllegalStateException("Handler must be an object singleton")

        span.setAttribute(
            MlflowFluentSpanAttributes.MLFLOW_SPAN_OUTPUTS.asAttributeKey(),
            handler.processOutput(result)
        )
    }

    actual override fun createTraceInfo(spanBuilder: SpanBuilder, method: PlatformMethod, spanName: String) = runBlocking {
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
