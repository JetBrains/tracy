package ai.dev.kit.tracing.fluent

import ai.dev.kit.tracing.fluent.handlers.PlatformMethod
import ai.dev.kit.tracing.fluent.processor.Span
import ai.dev.kit.tracing.fluent.processor.SpanBuilder

// applicable to both mlflow and wandb logging clients
expect fun configureTracingMetadata(
    spanBuilder: SpanBuilder,
    traceAnnotation: KotlinFlowTrace,
    method: PlatformMethod,
    args: Array<Any?>,
    client: KotlinLoggingClient
)

// applicable to both mlflow and wandb logging clients
expect fun addOutputAttributesToTracing(span: Span, traceAnnotation: KotlinFlowTrace, result: Any?)
