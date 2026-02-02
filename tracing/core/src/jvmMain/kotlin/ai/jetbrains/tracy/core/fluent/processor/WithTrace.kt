package ai.jetbrains.tracy.core.fluent.processor

import ai.jetbrains.tracy.core.TracingManager
import ai.jetbrains.tracy.core.addExceptionAttributes
import ai.jetbrains.tracy.core.currentSpanContext
import ai.jetbrains.tracy.core.fluent.FluentSpanAttributes
import ai.jetbrains.tracy.core.fluent.Trace
import ai.jetbrains.tracy.core.fluent.TracingSessionProvider
import ai.jetbrains.tracy.core.fluent.customizers.PlatformMethod
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import java.lang.reflect.Method
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaMethod

@Deprecated("use withSpan() instead", level = DeprecationLevel.HIDDEN)
actual inline fun <T> withTrace(
    function: KFunction<*>,
    args: Array<Any?>,
    traceAnnotation: Trace,
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
    traceAnnotation: Trace,
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

/**
 * Creates and configures a tracing [Span] for annotation-based tracing.
 */
@PublishedApi
internal fun createSpan(
    traceAnnotation: Trace,
    method: Method,
    args: Array<Any?>,
    context: Context = Context.current(),
): Span {
    val tracer = TracingManager.tracer

    /**
     * Resolution pipeline:
     * 1. If [ai.jetbrains.tracy.core.fluent.customizers.SpanMetadataCustomizer.resolveSpanName]
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

/**
 * Adds formatted output attributes to the given [Span].
 */
@PublishedApi
internal fun addOutputAttributesToTracing(
    span: Span,
    traceAnnotation: Trace,
    result: Any?
) {
    span.setAttribute(
        FluentSpanAttributes.SPAN_OUTPUTS.key, traceAnnotation.getSpanMetadataCustomizer().formatOutputAttribute(result)
    )
}

/**
 * Configures input and code metadata on the span builder.
 * Internal helper used during span creation.
 */
internal fun configureTracingMetadata(
    spanBuilder: SpanBuilder,
    traceAnnotation: Trace,
    method: PlatformMethod,
    args: Array<Any?>,
) {
    with(spanBuilder) {
        setAttribute(
            FluentSpanAttributes.SPAN_INPUTS.key,
            traceAnnotation.getSpanMetadataCustomizer().formatInputAttributes(method, args)
        )
        setAttribute(
            FluentSpanAttributes.CODE_FUNCTION_NAME.key, "${method.declaringClass.name}.${method.name}"
        )
    }
}

/**
 * Returns the metadata customizer instance from this [Trace].
 */
internal fun Trace.getSpanMetadataCustomizer() =
    this.metadataCustomizer.objectInstance ?: error("Handler must be an object singleton")

