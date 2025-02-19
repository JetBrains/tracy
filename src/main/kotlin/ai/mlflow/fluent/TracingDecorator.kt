package org.example.ai.mlflow.fluent

import com.google.inject.AbstractModule
import com.google.inject.BindingAnnotation
import com.google.inject.matcher.Matchers
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.context.Scope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.aopalliance.intercept.MethodInterceptor
import org.aopalliance.intercept.MethodInvocation
import org.example.ai.mlflow.MlflowClients
import org.example.ai.mlflow.createTrace
import org.example.ai.mlflow.dataclasses.TraceInfo
import org.example.ai.mlflow.dataclasses.createTracePostRequest
import java.util.logging.LogManager
import java.util.logging.Logger
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter.Kind
import kotlin.reflect.full.declaredFunctions

@BindingAnnotation
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class KotlinFlowTrace(val name: String = "", val spanType: String = SpanType.UNKNOWN)


class KotlinFlowTracer : MethodInterceptor {
    private val tracer: Tracer = GlobalOpenTelemetry.getTracer("org.example.ai.mlflow")

    private fun createSpan(invocation: MethodInvocation): Span {
        val traceAnnotation = invocation.method.getAnnotation(KotlinFlowTrace::class.java)
        val spanName = traceAnnotation.name.ifBlank { invocation.method.name }

        val spanBuilder = tracer.spanBuilder(spanName)
        spanBuilder.setAttribute("mlflow.spanType", traceAnnotation.spanType)

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
                    experimentId = MlflowClients.currentExperimentId,
                    traceCreationPath = invocation.method.declaringClass.name,
                    traceName = spanName
                )
                val traceInfo = createTrace(tracePostRequest)
                spanBuilder.setAttribute("traceCreationInfo", Json.encodeToString(TraceInfo.serializer(), traceInfo))
            }
        }
        return spanBuilder.startSpan()
    }

    @Throws(Throwable::class)
    override fun invoke(invocation: MethodInvocation): Any {
        val methodName = invocation.method.name

        val span = createSpan(invocation)
        span.setAttribute("mlflow.spanInputs", extractInputs(invocation))

        val scope: Scope = span.makeCurrent()
        return try {
            val result = invocation.proceed()
            span.setAttribute("mlflow.spanOutputs", result.toString())
            result
        } catch (exception: Throwable) {
            span.recordException(exception)
            span.setStatus(StatusCode.ERROR, exception.message ?: "Message not found")
            logger.warning("Failed to start span on $methodName")
        } finally {
            span.end()
            scope.close()
        }
    }

    private fun extractInputs(invocation: MethodInvocation): String {
        val methodName = invocation.method.name
        val declaringClass = invocation.method.declaringClass.kotlin

        val methodFunction: KFunction<*>? = declaringClass.declaredFunctions.find { it.name == methodName }

        if (methodFunction == null) {
            logger.warning("Failed to find method '$methodName' in class '${declaringClass.qualifiedName}'. Returning an empty JSON object.")
            return "{}"
        }

        return methodFunction.parameters.filter { it.kind != Kind.INSTANCE }.mapIndexed { index, parameter ->
                val paramName = parameter.name ?: "arg$index"
                val argumentValue = invocation.arguments.getOrNull(index)?.toString() ?: "null"
                "\"$paramName\": $argumentValue"
            }.joinToString(", ", prefix = "{", postfix = "}")
    }

    companion object {
        private val logger: Logger = LogManager.getLogManager().getLogger(Logger.GLOBAL_LOGGER_NAME)
            ?: Logger.getLogger(KotlinFlowTracer::class.java.name)
    }

}

class KotlinFlowTraceModule : AbstractModule() {
    override fun configure() {
        bindInterceptor(
            Matchers.any(), Matchers.annotatedWith(KotlinFlowTrace::class.java), KotlinFlowTracer()
        )
    }
}