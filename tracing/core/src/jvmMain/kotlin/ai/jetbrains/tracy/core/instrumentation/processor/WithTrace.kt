/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package ai.jetbrains.tracy.core.instrumentation.processor

import ai.jetbrains.tracy.core.TracingManager
import ai.jetbrains.tracy.core.currentSpanContext
import ai.jetbrains.tracy.core.instrumentation.TracySpanAttributes
import ai.jetbrains.tracy.core.instrumentation.Trace
import ai.jetbrains.tracy.core.instrumentation.TracingSessionProvider
import ai.jetbrains.tracy.core.instrumentation.customizers.PlatformMethod
import ai.jetbrains.tracy.core.instrumentation.customizers.SpanMetadataCustomizer
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
    val spanMetadataCustomizer = traceAnnotation.getSpanMetadataCustomizer()
    val span = createSpan(traceAnnotation, spanMetadataCustomizer, method, args)
    val scope = span.makeCurrent()
    try {
        val result = block()
        return result.also {
            addOutputAttributesToTracing(span, spanMetadataCustomizer, it)
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
    val spanMetadataCustomizer = traceAnnotation.getSpanMetadataCustomizer()
    val span = createSpan(
        traceAnnotation = traceAnnotation,
        spanMetadataCustomizer = spanMetadataCustomizer,
        method = method,
        args = args,
        context = currentSpanContext(currentCoroutineContext())
    )
    try {
        val result = withContext(span.asContextElement()) {
            block()
        }
        return result.also {
            addOutputAttributesToTracing(span, spanMetadataCustomizer, it)
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
 * Records the given [exception] on this span and marks it as failed.
 */
@PublishedApi
internal fun Span.addExceptionAttributes(exception: Throwable) {
    this.recordException(exception)
    this.setStatus(StatusCode.ERROR, exception.message ?: "Unknown error")
}

/**
 * Creates and configures a tracing [Span] for annotation-based tracing.
 */
@PublishedApi
internal fun createSpan(
    traceAnnotation: Trace,
    spanMetadataCustomizer: SpanMetadataCustomizer,
    method: Method,
    args: Array<Any?>,
    context: Context = Context.current(),
): Span {
    val tracer = TracingManager.tracer
    /**
     * Resolution pipeline:
     * 1. If [SpanMetadataCustomizer.resolveSpanName]
     *    returns a non-null value, that name is used.
     * 2. Otherwise, the tracing system checks the annotation name.
     * 3. If blank, the method name is used.
     */
    val spanName = spanMetadataCustomizer.resolveSpanName(method, args)
        ?: traceAnnotation.name.ifBlank { method.name }
    val spanBuilder = tracer.spanBuilder(spanName)
    TracingSessionProvider.currentSessionId?.let {
        spanBuilder.setAttribute(TracySpanAttributes.SESSION_ID.key, it)
    }
    configureTracingMetadata(spanBuilder, spanMetadataCustomizer, method, args)
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
    spanMetadataCustomizer: SpanMetadataCustomizer,
    result: Any?
) {
    span.setAttribute(
        TracySpanAttributes.SPAN_OUTPUTS.key, spanMetadataCustomizer.formatOutputAttribute(result)
    )
}

/**
 * Returns the [SpanMetadataCustomizer] instance configured for this [Trace].
 *
 * Only Kotlin `object` declarations are supported. Passing a class will throw an error.
 */
@PublishedApi
internal fun Trace.getSpanMetadataCustomizer(): SpanMetadataCustomizer = metadataCustomizer.objectInstance
    ?: error("SpanMetadataCustomizer '${metadataCustomizer.qualifiedName}' must be a Kotlin object.")

/**
 * Configures input and code metadata on the span builder.
 * Internal helper used during span creation.
 */
internal fun configureTracingMetadata(
    spanBuilder: SpanBuilder,
    spanMetadataCustomizer: SpanMetadataCustomizer,
    method: PlatformMethod,
    args: Array<Any?>,
) {
    with(spanBuilder) {
        setAttribute(
            TracySpanAttributes.SPAN_INPUTS.key,
            spanMetadataCustomizer.formatInputAttributes(method, args)
        )
        setAttribute(
            TracySpanAttributes.CODE_FUNCTION_NAME.key, "${method.declaringClass.name}.${method.name}"
        )
    }
}
