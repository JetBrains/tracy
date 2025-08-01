package ai.dev.kit.tracing.fluent.processor

import ai.dev.kit.tracing.AI_DEVELOPMENT_KIT_TRACER
import ai.dev.kit.tracing.TracingManager
import ai.dev.kit.tracing.fluent.FluentSpanAttributes
import ai.dev.kit.tracing.fluent.KotlinFlowTrace
import ai.dev.kit.tracing.fluent.TracingSessionProvider
import ai.dev.kit.tracing.fluent.addOutputAttributesToTracing
import ai.dev.kit.tracing.fluent.configureTracingMetadata
import io.opentelemetry.api.GlobalOpenTelemetry
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
    block: () -> T
): T {
    val method = function.javaMethod ?: throw IllegalArgumentException("Function must be a Java method")
    val traceAnnotation = method.getAnnotation(KotlinFlowTrace::class.java)
        ?: throw IllegalArgumentException("Function must be annotated with @KotlinFlowTrace annotation")
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
    val tracer = GlobalOpenTelemetry.getTracer("ai.dev.kit.fluent.tracer")

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
    crossinline block: suspend () -> T
): T {
    val method = function.javaMethod ?: throw IllegalArgumentException("Function must be a Java method")
    val traceAnnotation = method.getAnnotation(KotlinFlowTrace::class.java)
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

fun getOpenTelemetryContext(coroutineContext: CoroutineContext): Context {
    return coroutineContext.getOpenTelemetryContext().let {
        if (it == Context.root()) Context.current() else it
    }
}

fun Span.addExceptionAttributes(exception : Throwable) {
    this.recordException(exception)
    this.setStatus(StatusCode.ERROR, exception.message ?: "Unknown error")
}

fun createSpan(
    traceAnnotation: KotlinFlowTrace,
    method: Method,
    args: Array<Any?>,
    context: Context = Context.current(),
): Span {
    val tracer = TracingManager.sdk.getTracer(AI_DEVELOPMENT_KIT_TRACER, "0.0.0")
    val spanName = traceAnnotation.name.ifBlank { method.name }
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
