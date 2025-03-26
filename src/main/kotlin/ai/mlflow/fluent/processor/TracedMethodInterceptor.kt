package org.example.ai.mlflow.fluent.processor

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.context.Scope
import io.opentelemetry.extension.kotlin.asContextElement
import io.opentelemetry.extension.kotlin.getOpenTelemetryContext
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.example.ai.mlflow.KotlinMlflowClient
import org.example.ai.mlflow.createTrace
import org.example.ai.mlflow.dataclasses.TraceInfo
import org.example.ai.mlflow.dataclasses.createTracePostRequest
import org.example.ai.mlflow.fluent.FluentSpanAttributes
import org.example.ai.mlflow.fluent.KotlinFlowTrace
import org.example.ai.mlflow.fluent.processor.TracedMethodInterceptor.createSpan
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext

object TracedMethodInterceptor {
    private val tracer: Tracer = GlobalOpenTelemetry.getTracer("org.example.ai.mlflow")

    fun createSpan(traceAnnotation: KotlinFlowTrace,
                   method: Method,
                   args: Array<Any?>,
                   parentSpan: Span = Span.current()
    ): Span {
        val spanName = traceAnnotation.name.ifBlank { method.name }
        val spanBuilder = tracer.spanBuilder(spanName)

        KotlinMlflowClient.currentRunId?.let {
            spanBuilder.setAttribute(FluentSpanAttributes.MLFLOW_SOURCE_RUN.asAttributeKey(), it)
        }
        spanBuilder.setAttribute(
            FluentSpanAttributes.MLFLOW_SPAN_INPUTS.asAttributeKey(),
            traceAnnotation.attributeHandler.handler.processInput(method, args)
        )
        spanBuilder.setAttribute(
            FluentSpanAttributes.MLFLOW_SPAN_SOURCE_NAME.asAttributeKey(),
            method.declaringClass.name
        )
        spanBuilder.setAttribute(
            FluentSpanAttributes.MLFLOW_SPAN_TYPE.asAttributeKey(),
            traceAnnotation.spanType
        )
        spanBuilder.setAttribute(
            FluentSpanAttributes.MLFLOW_SPAN_FUNCTION_NAME.asAttributeKey(),
            method.name
        )

        if (parentSpan.spanContext.isValid) {
            // If parent exists, set parent
            spanBuilder.setParent(Context.current())
        } else {
            // If root, then create a Trace and add traceCreationInfo to attribute
            spanBuilder.setNoParent()
            // TODO Get rid of run blocking
            runBlocking {
                val tracePostRequest = createTracePostRequest(
                    experimentId = KotlinMlflowClient.currentExperimentId,
                    runId = KotlinMlflowClient.currentRunId,
                    traceCreationPath = method.declaringClass.name,
                    traceName = spanName
                )
                val jsonTraceInfo = Json.encodeToString(TraceInfo.serializer(), createTrace(tracePostRequest))
                spanBuilder.setAttribute(FluentSpanAttributes.TRACE_CREATION_INFO.asAttributeKey(), jsonTraceInfo)
            }
        }
        return spanBuilder.startSpan()
    }
}

val a = ConcurrentHashMap<Method, org.example.ai.mlflow.fluent.processor.TraceInfo>()

fun deleteContinuation(method: Any?) = a.remove(method)


fun argsProcessor(traceAnnotation: KotlinFlowTrace,
                  method: Method,
                  args: Array<Any?>)
: Array<Any?> {
    if (args.lastOrNull() !is Continuation<*>) {
        val span = createSpan(traceAnnotation, method, args)
        a[method] = TraceInfo(span, span.makeCurrent())
        return args
    }
    val continuation = args.last() as Continuation<*>
    print(continuation)
//    val parentSpan = Span.fromContext(continuation.context.getOpenTelemetryContext())
    if (!a.containsKey(method)) {
        val span = createSpan(traceAnnotation, method, args)
        a[method] = TraceInfo(span, span.makeCurrent())
        args[args.size - 1] = continuation
            .withContext(span.asContextElement())
        return args
    }
    // Либо батин вписан либо мой
    return args
}

class TraceInfoContextElement(val traceInfo: org.example.ai.mlflow.fluent.processor.TraceInfo) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<TraceInfoContextElement>
}

fun <T> Continuation<T>.withContext(context: CoroutineContext) = object: Continuation<T> {
    override val context: CoroutineContext = this@withContext.context + context
    override fun resumeWith(result: Result<T>) = this@withContext.resumeWith(result)
}


data class TraceInfo(
    val span: Span,
    val scope: Scope
) {
    fun addOutputAttribute(traceAnnotation: KotlinFlowTrace, result: Any?) {
        span.setAttribute(
            FluentSpanAttributes.MLFLOW_SPAN_OUTPUTS.asAttributeKey(),
            traceAnnotation.attributeHandler.handler.processOutput(result)
        )
    }
    fun close() {
        span.end()
        scope.close()
    }
}
