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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.aopalliance.intercept.MethodInterceptor
import org.aopalliance.intercept.MethodInvocation
import java.time.Instant

fun generateRandomString(length: Int = 10): String {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    return (1..length).map { chars.random() }.joinToString("")
}

data class Argument(
    val name: String, val value: Any
)


data class StartTraceInfo(
    val path: String,
    val methodName: String,
    val startTime: Long,
    val arguments: List<Argument>,
)

data class EndTraceInfo(
    val path: String,
    val methodName: String,
    val startTime: Long,
    val endTime: Long,
    val arguments: List<Argument>,
    val result: Any
) {
    fun argumentsAsJson(): kotlinx.serialization.json.JsonObject {
        return buildJsonObject {
            arguments.forEach { argument ->
                put(argument.name, JsonPrimitive(argument.value.toString()))
            }
        }
    }
}

@BindingAnnotation
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class KotlinFlowTrace

class KotlinFlowTracer : MethodInterceptor {

    private val tracer: Tracer = GlobalOpenTelemetry.getTracer("org.example.ai.mlflow")

    private fun createSpan(spanName: String): Span {
        val spanBuilder = tracer.spanBuilder(spanName)
        val parentSpan = Span.current()
        // If parent exists, set parent
        if (parentSpan.spanContext.isValid) {
            spanBuilder.setParent(Context.current())
        } else {
            spanBuilder.setNoParent()
            // Report root span

        }
        spanBuilder.setAttribute("sdet.tool", "MLFlow")
        return spanBuilder.startSpan()
    }

    @Throws(Throwable::class)
    override fun invoke(invocation: MethodInvocation): Any {
        val methodName = invocation.method.name
        val span = createSpan(methodName)

        invocation.arguments.forEachIndexed { index, argument ->
            span.setAttribute("arg$index", argument.toString())
        }

        val scope: Scope = span.makeCurrent()
        val startTime = Instant.now().toEpochMilli()
        return try {
            val result = invocation.proceed()
            span.setAttribute("result", result.toString())
            result
        } catch (exception: Throwable) {
            span.recordException(exception)
            span.setStatus(StatusCode.ERROR, exception.message ?: "Message not found")
            throw exception
        } finally {
            val endTime = Instant.now().toEpochMilli()
            span.end()
            scope.close()
        }
    }
}

class KotlinFlowTraceModule : AbstractModule() {
    override fun configure() {
        bindInterceptor(
            Matchers.any(), Matchers.annotatedWith(KotlinFlowTrace::class.java), KotlinFlowTracer()
        )
    }
}