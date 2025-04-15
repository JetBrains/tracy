package ai.dev.kit.eval.base.fluent

import ai.dev.kit.core.fluent.KotlinFlowTrace
import ai.dev.kit.core.fluent.handlers.PlatformMethod
import ai.dev.kit.eval.base.KotlinLoggingClient
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder

// applicable to both mlflow and wandb logging clients
fun configureTracingMetadata(
    spanBuilder: SpanBuilder,
    traceAnnotation: KotlinFlowTrace,
    method: PlatformMethod,
    args: Array<Any?>,
    client: KotlinLoggingClient
) {
    val handler = traceAnnotation.attributeHandler.objectInstance
        ?: throw IllegalStateException("Handler must be an object singleton")

    client.currentRunId?.let {
        spanBuilder.setAttribute(FluentSpanAttributes.SOURCE_RUN.asAttributeKey(), it)
    }
    spanBuilder.setAttribute(
        FluentSpanAttributes.SPAN_INPUTS.asAttributeKey(),
        handler.processInput(method, args)
    )
    spanBuilder.setAttribute(
        FluentSpanAttributes.SPAN_SOURCE_NAME.asAttributeKey(), method.declaringClass.name
    )
    spanBuilder.setAttribute(
        FluentSpanAttributes.SPAN_TYPE.asAttributeKey(), traceAnnotation.spanType
    )
    spanBuilder.setAttribute(
        FluentSpanAttributes.SPAN_FUNCTION_NAME.asAttributeKey(), method.name
    )
}

// applicable to both mlflow and wandb logging clients
fun addOutputAttributesToTracing(span: Span, traceAnnotation: KotlinFlowTrace, result: Any?) {
    val handler = traceAnnotation.attributeHandler.objectInstance
        ?: throw IllegalStateException("Handler must be an object singleton")

    span.setAttribute(
        FluentSpanAttributes.SPAN_OUTPUTS.asAttributeKey(),
        handler.processOutput(result)
    )
}