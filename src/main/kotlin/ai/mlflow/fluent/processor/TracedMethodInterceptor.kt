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
        val spanName = traceAnnotation.name.ifBlank { method.name }
        val span = createSpan(spanName, method)

        span.setAttribute(FluentSpanAttributes.MLFLOW_SPAN_SOURCE_NAME.asAttributeKey(), method.declaringClass.name)
        span.setAttribute(FluentSpanAttributes.MLFLOW_SPAN_TYPE.asAttributeKey(), traceAnnotation.spanType)
        span.setAttribute(FluentSpanAttributes.MLFLOW_SPAN_FUNCTION_NAME.asAttributeKey(), method.name)
        KotlinMlflowClient.currentRunId?.let {
            span.setAttribute(FluentSpanAttributes.MLFLOW_SOURCE_RUN.asAttributeKey(), it)
        }

        val argumentHandler = traceAnnotation.attributeHandler.handler

        span.setAttribute(FluentSpanAttributes.MLFLOW_SPAN_INPUTS.asAttributeKey(), argumentHandler.processInput(method, args))
        val scope: Scope = span.makeCurrent()
        return try {
            val result = originalMethod.call()
            span.setAttribute(FluentSpanAttributes.MLFLOW_SPAN_OUTPUTS.asAttributeKey(), argumentHandler.processOutput(result))
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

    private fun createSpan(spanName: String, method: Method): Span {
        val spanBuilder = tracer.spanBuilder(spanName)

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
                    traceCreationPath = method.declaringClass.name,
                    traceName = spanName,
                    runId = KotlinMlflowClient.currentRunId,
                )
                val jsonTraceInfo = Json.encodeToString(TraceInfo.serializer(), createTrace(tracePostRequest))
                spanBuilder.setAttribute(FluentSpanAttributes.TRACE_CREATION_INFO.asAttributeKey(), jsonTraceInfo)
            }
        }
        return spanBuilder.startSpan()
    }
}
