package ai.jetbrains.tracy.core.fluent.processor

import ai.jetbrains.tracy.core.tracing.TracingManager
import ai.jetbrains.tracy.core.fluent.FluentSpanAttributes
import ai.jetbrains.tracy.core.fluent.KotlinFlowTrace
import ai.jetbrains.tracy.core.fluent.TracingSessionProvider
import ai.jetbrains.tracy.core.fluent.addOutputAttributesToTracing
import ai.jetbrains.tracy.core.fluent.configureTracingMetadata
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import io.opentelemetry.extension.kotlin.getOpenTelemetryContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import java.lang.reflect.Method
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaMethod

/**
 * Returns the active OpenTelemetry [Context] from the given [CoroutineContext].
 * Falls back to [Context.current] when no trace is attached or context is root.
 */
fun currentSpanContext(coroutineContext: CoroutineContext? = null): Context {
    val ctx = coroutineContext?.getOpenTelemetryContext() ?: return Context.current()
    return if (ctx == Context.root()) Context.current() else ctx
}

/**
 * Wraps the current OpenTelemetry [Context] as a coroutine [CoroutineContext].
 * Use this to preserve trace context across coroutines.
 */
fun currentSpanContextElement(coroutineContext: CoroutineContext? = null) =
    currentSpanContext(coroutineContext).asContextElement()

inline fun <T> withSpan(
    name: String,
    attributes: Map<String, Any?> = emptyMap(),
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
        span.addExceptionAttributes(e)
        throw e
    } finally {
        scope.close()
        span.end()
    }
}

@Deprecated("use withSpan() instead", level = DeprecationLevel.HIDDEN)
actual inline fun <T> withTrace(
    function: KFunction<*>,
    args: Array<Any?>,
    traceAnnotation: KotlinFlowTrace,
    crossinline block: () -> T
): T {
    if (!TracingManager.isTracingEnabled) {
        return block()
    }
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

@Deprecated("use withSpan() instead", level = DeprecationLevel.HIDDEN)
actual suspend inline fun <T> withTraceSuspended(
    function: KFunction<*>,
    args: Array<Any?>,
    traceAnnotation: KotlinFlowTrace,
    crossinline block: suspend () -> T
): T {
    if (!TracingManager.isTracingEnabled) {
        return block()
    }
    val method = function.javaMethod ?: throw IllegalArgumentException("Function must be a Java method")
    val span = createSpan(
        traceAnnotation, method, args, currentSpanContext(currentCoroutineContext())
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
     * 1. If [ai.jetbrains.tracy.core.fluent.handlers.SpanMetadataCustomizer.resolveSpanName]
     *    returns a non-null value, that name is used.
     * 2. Otherwise, the tracing system checks the annotation name.
     * 3. If blank, the method name is used.
     */
    val spanName = traceAnnotation.getSpanMetadataCustomizer().resolveSpanName(method, args)
        ?: traceAnnotation.name.ifBlank { method.name }
    val spanBuilder = tracer.spanBuilder(spanName)
    TracingSessionProvider.currentSessionId?.let {
        spanBuilder.setAttribute(FluentSpanAttributes.SESSION_ID.key, it)
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

fun KotlinFlowTrace.getSpanMetadataCustomizer() = this.metadataCustomizer.objectInstance
    ?: error("Handler must be an object singleton")

fun Span.addExceptionAttributes(exception: Throwable) {
    this.recordException(exception)
    this.setStatus(StatusCode.ERROR, exception.message ?: "Unknown error")
}
