package ai.dev.kit.tracing.fluent.processor

import ai.dev.kit.tracing.TracingManager
import ai.dev.kit.tracing.fluent.*
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import io.opentelemetry.extension.kotlin.getOpenTelemetryContext
import kotlinx.coroutines.withContext
import java.lang.reflect.Method
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaMethod

@Deprecated("use withSpan() instead")
actual inline fun <T> withTrace(
    function: KFunction<*>,
    args: Array<Any?>,
    traceAnnotation: KotlinFlowTrace,
    block: () -> T
): T {
    val method = function.javaMethod ?: throw IllegalArgumentException("Function must be a Java method")
    val span = createSpan(traceAnnotation, method, args)
    val scope = span.makeCurrent()
    try {
        val result = block()
        return result.also {
            addOutputAttributesToTracing(span, traceAnnotation, it)
            span.setStatus(StatusCode.OK)
        }
    } catch (exception: Throwable) {
        span.addExceptionAttributes(exception)
        throw exception
    } finally {
        span.end()
        scope.close()
    }
}

inline fun <T> withSpan(
    name: String,
    attributes: Map<String, Any?>,
    block: (Span) -> T
): T {
    val tracer = TracingManager.tracer

    val span = tracer.spanBuilder(name).startSpan()
    val scope = span.makeCurrent()

    attributes.forEach { (key, value) ->
        // TODO: deal with types
        span.setAttribute(key, value.toString())
    }

    try {
        val result = block(span)
        span.setAttribute("output", result.toString())

        return result
    } catch (e: Exception) {
        span.recordException(e)
        span.setStatus(StatusCode.ERROR, "Block $name execution failed")
        throw e
    } finally {
        scope.close()
        span.end()
    }
}

// TODO: try to reduce code duplication
actual suspend inline fun <T> withTraceSuspended(
    function: KFunction<*>,
    args: Array<Any?>,
    traceAnnotation: KotlinFlowTrace,
    crossinline block: suspend () -> T
): T {
    val method = function.javaMethod ?: throw IllegalArgumentException("Function must be a Java method")
    val span = createSpan(
        traceAnnotation, method, args, getOpenTelemetryContext(coroutineContext)
    )
    try {
        val result = withContext(span.asContextElement()) {
            block()
        }
        return result.also {
            addOutputAttributesToTracing(span, traceAnnotation, it)
            span.setStatus(StatusCode.OK)
        }
    } catch (exception: Throwable) {
        span.addExceptionAttributes(exception)
        throw exception
    } finally {
        span.end()
    }
}

fun createSpan(
    traceAnnotation: KotlinFlowTrace,
    method: Method,
    args: Array<Any?>,
    context: Context = Context.current(),
): Span {
    val tracer = TracingManager.tracer

    /**
     * Resolution pipeline:
     * 1. If [ai.dev.kit.tracing.fluent.handlers.SpanMetadataCustomizer.resolveSpanName]
     *    returns a non-null value, that name is used.
     * 2. Otherwise, the tracing system checks the annotation name.
     * 3. If blank, the method name is used.
     */
    val spanName = traceAnnotation.getSpanAttributeHandler().resolveSpanName(method, args)
        ?: traceAnnotation.name.ifBlank { method.name }
    val spanBuilder = tracer.spanBuilder(spanName)
    TracingSessionProvider.currentSessionId?.let {
        spanBuilder.setAttribute(FluentSpanAttributes.SOURCE_RUN.key, it)
    }
    configureTracingMetadata(spanBuilder, traceAnnotation, method, args)
    val parentSpan = Span.fromContext(context)
    val span = if (parentSpan.spanContext.isValid) {
        // If parent exists, set parent
        spanBuilder.setParent(context)
        spanBuilder.startSpan()
    } else {
        // If root, set no parent
        spanBuilder.setNoParent().startSpan()
    }
    return span
}

fun KotlinFlowTrace.getSpanAttributeHandler() = this.attributeHandler.objectInstance
    ?: error("Handler must be an object singleton")

fun getOpenTelemetryContext(coroutineContext: CoroutineContext): Context {
    return coroutineContext.getOpenTelemetryContext().let {
        if (it == Context.root()) Context.current() else it
    }
}

fun Span.addExceptionAttributes(exception: Throwable) {
    this.recordException(exception)
    this.setStatus(StatusCode.ERROR, exception.message ?: "Unknown error")
}
