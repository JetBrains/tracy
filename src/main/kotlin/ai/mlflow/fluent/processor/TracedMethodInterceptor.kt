package org.example.ai.mlflow.fluent.processor

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.context.Scope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import net.bytebuddy.implementation.bind.annotation.AllArguments
import net.bytebuddy.implementation.bind.annotation.Origin
import net.bytebuddy.implementation.bind.annotation.RuntimeType
import net.bytebuddy.implementation.bind.annotation.SuperCall
import org.example.ai.mlflow.KotlinMlflowClient
import org.example.ai.mlflow.createTrace
import org.example.ai.mlflow.dataclasses.TraceInfo
import org.example.ai.mlflow.dataclasses.createTracePostRequest
import org.example.ai.mlflow.fluent.FluentSpanAttributes
import org.example.ai.mlflow.fluent.KotlinFlowTrace
import java.lang.reflect.Method
import java.util.concurrent.Callable

object TracedMethodInterceptor {
    private val tracer: Tracer = GlobalOpenTelemetry.getTracer("org.example.ai.mlflow")

    @JvmStatic
    @RuntimeType
    fun intercept(@SuperCall originalMethod: Callable<*>,
                  @Origin method: Method,
                  @AllArguments args: Array<Any?>
    ): Any? {
        val traceAnnotation = method.getAnnotation(KotlinFlowTrace::class.java)
        val span = createSpan(traceAnnotation, method, args)
        val scope: Scope = span.makeCurrent()

        return try {
            val result = originalMethod.call()
            span.setAttribute(
                FluentSpanAttributes.MLFLOW_SPAN_OUTPUTS.asAttributeKey(),
                traceAnnotation.attributeHandler.handler.processOutput(result)
            )
            result
        } catch (exception: Throwable) {
            span.recordException(exception)
            span.setStatus(StatusCode.ERROR, exception.message ?: "Unknown error")
            throw exception
        } finally {
            span.end()
            scope.close()
        }
    }

    fun createSpan(traceAnnotation: KotlinFlowTrace, method: Method, args: Array<Any?>): Span {
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

        val parentSpan = Span.current()
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
