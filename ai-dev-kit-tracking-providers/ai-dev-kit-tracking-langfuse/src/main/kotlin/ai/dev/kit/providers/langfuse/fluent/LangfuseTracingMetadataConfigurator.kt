package ai.dev.kit.providers.langfuse.fluent

import ai.dev.kit.providers.langfuse.fluent.LangfuseTracePublisher.Companion.publishRootStartCall
import ai.dev.kit.tracing.fluent.KotlinFlowTrace
import ai.dev.kit.tracing.fluent.addOutputAttributesToTracing
import ai.dev.kit.tracing.fluent.handlers.PlatformMethod
import ai.dev.kit.tracing.fluent.processor.Span
import ai.dev.kit.tracing.fluent.processor.TracingFlowProcessor
import ai.dev.kit.tracing.fluent.processor.TracingMetadataConfigurator
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.sdk.trace.ReadableSpan
import kotlinx.coroutines.launch

class LangfuseTracingMetadataConfigurator : TracingMetadataConfigurator {
    override fun addOutputAttribute(
        span: Span, traceAnnotation: KotlinFlowTrace, result: Any?
    ) {
        addOutputAttributesToTracing(span, traceAnnotation, result)
    }

    override fun createTraceInfo(spanBuilder: SpanBuilder, method: PlatformMethod, spanName: String): Span {
        val span = spanBuilder.startSpan()
        TracingFlowProcessor.scope.launch {
            publishRootStartCall(
                span as ReadableSpan
            )
        }
        return span
    }
}
