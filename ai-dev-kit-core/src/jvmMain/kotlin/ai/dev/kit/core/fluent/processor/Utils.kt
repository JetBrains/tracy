package ai.dev.kit.core.fluent.processor

import ai.dev.kit.core.fluent.KotlinFlowTrace
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import io.opentelemetry.extension.kotlin.getOpenTelemetryContext
import kotlinx.coroutines.withContext
import java.lang.reflect.Method
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaMethod

actual fun <T> withTrace(
    function: KFunction<*>,
    args: Array<Any?>,
    tracingMetadataConfigurator: TracingMetadataConfigurator,
    block: () -> T
): T {
    val method = function.javaMethod ?: throw IllegalArgumentException("Function must be a Java method")
    val traceAnnotation = method.getAnnotation(KotlinFlowTrace::class.java)
        ?: throw IllegalArgumentException("Function must be annotated with @KotlinFlowTrace annotation")
    val span = createSpan(traceAnnotation, method, args, tracingMetadataConfigurator)
    val scope = span.makeCurrent()
    try {
        val result = block()
        return result.also {
            tracingMetadataConfigurator.addOutputAttribute(span, traceAnnotation, it)
        }
    } catch (exception: Throwable) {
        span.recordException(exception)
        span.setStatus(StatusCode.ERROR, exception.message ?: "Unknown error")
        throw exception
    } finally {
        span.end()
        scope.close()
    }
}

actual suspend fun <T> withTraceSuspended(
    function: KFunction<*>,
    args: Array<Any?>,
    tracingMetadataConfigurator: TracingMetadataConfigurator,
    block: suspend () -> T
): T {
    val method = function.javaMethod ?: throw IllegalArgumentException("Function must be a Java method")
    val traceAnnotation = method.getAnnotation(KotlinFlowTrace::class.java)
    val span = createSpan(
        traceAnnotation, method, args, tracingMetadataConfigurator, getOpenTelemetryContext(coroutineContext)
    )
    try {
        val result = withContext(span.asContextElement()) {
            block()
        }
        return result.also {
            tracingMetadataConfigurator.addOutputAttribute(span, traceAnnotation, it)
        }
    } catch (exception: Throwable) {
        span.recordException(exception)
        span.setStatus(StatusCode.ERROR, exception.message ?: "Unknown error")
        throw exception
    } finally {
        span.end()
    }
}

private fun getOpenTelemetryContext(coroutineContext: CoroutineContext): Context {
    return coroutineContext.getOpenTelemetryContext().let {
        if (it == Context.root()) Context.current() else it
    }
}

private fun createSpan(
    traceAnnotation: KotlinFlowTrace,
    method: Method,
    args: Array<Any?>,
    tracingMetadataConfigurator: TracingMetadataConfigurator,
    context: Context = Context.current(),
    tracer: Tracer = TracingFlowProcessor.tracer
): Span {
    val spanName = traceAnnotation.name.ifBlank { method.name }
    val spanBuilder = tracer.spanBuilder(spanName)

    tracingMetadataConfigurator.configureMetadata(spanBuilder, traceAnnotation, method, args)

    val parentSpan = Span.fromContext(context)

    val span = if (parentSpan.spanContext.isValid) {
        // If parent exists, set parent
        spanBuilder.setParent(context)
        spanBuilder.startSpan()
    } else {
        // If root, then create a trace
        spanBuilder.setNoParent()
        tracingMetadataConfigurator.createTraceInfo(spanBuilder, method, spanName)
    }
    return span
}
