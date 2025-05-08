package ai.dev.kit.providers.langfuse.fluent

import ai.dev.kit.tracing.fluent.KotlinFlowTrace
import ai.dev.kit.tracing.fluent.addOutputAttributesToTracing
import ai.dev.kit.tracing.fluent.configureTracingMetadata
import ai.dev.kit.tracing.fluent.handlers.PlatformMethod
import ai.dev.kit.tracing.fluent.processor.Span
import ai.dev.kit.tracing.fluent.processor.TracingMetadataConfigurator
import ai.dev.kit.providers.langfuse.KotlinLangfuseClient
import ai.dev.kit.providers.langfuse.fluent.LangfuseTracePublisher.Companion.publishRootStartCall
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.sdk.trace.ReadableSpan
import kotlinx.coroutines.runBlocking

class LangfuseTracingMetadataConfigurator : TracingMetadataConfigurator {
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
            KotlinLangfuseClient
        )
    }

    override fun addOutputAttribute(
        span: Span, traceAnnotation: KotlinFlowTrace, result: Any?
    ) {
        addOutputAttributesToTracing(span, traceAnnotation, result)
    }

    override fun createTraceInfo(spanBuilder: SpanBuilder, method: PlatformMethod, spanName: String): Span =
        runBlocking {
            val span = spanBuilder.startSpan()
            publishRootStartCall(
                span as ReadableSpan
            )
            return@runBlocking span
        }
}
