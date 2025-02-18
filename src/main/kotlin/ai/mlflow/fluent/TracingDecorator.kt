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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.aopalliance.intercept.MethodInterceptor
import org.aopalliance.intercept.MethodInvocation
import org.example.ai.mlflow.createTrace
import org.example.ai.mlflow.dataclasses.RequestMetadata
import org.example.ai.mlflow.dataclasses.Tag
import org.example.ai.mlflow.dataclasses.TracePostRequest
import org.mlflow.tracking.MlflowClient
import java.time.Instant

fun generateRandomString(length: Int = 10): String {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    return (1..length).map { chars.random() }.joinToString("")
}

data class Argument(
    val name: String, val value: Any
)

data class EndTraceInfo(
    val path: String,
    val methodName: String,
    val startTime: Long,
    val endTime: Long,
    val arguments: List<Argument>,
    val result: Any
) {
    fun argumentsAsJson(): JsonObject {
        return buildJsonObject {
            arguments.forEach { argument ->
                put(argument.name, JsonPrimitive(argument.value.toString()))
            }
        }
    }
}

data class TraceCreationInfo(
    val experimentId: String,
    val startTime: Long = Instant.now().toEpochMilli(),
    val traceCreationPath: String,
    val traceName: String
) {
    fun createTracePostRequest() = TracePostRequest(
        experimentId = experimentId, timestampMs = startTime, requestMetadata = listOf(
            RequestMetadata(key = "mlflow.trace_schema.version", value = "2")
        ), tags = listOf(
            Tag("mlflow.source.name", traceCreationPath),
            Tag("mlflow.source.type", "LOCAL"),
            Tag("mlflow.traceName", traceName)
        )
    )

}

@BindingAnnotation
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class KotlinFlowTrace

class KotlinFlowTracer : MethodInterceptor {

    private val tracer: Tracer = GlobalOpenTelemetry.getTracer("org.example.ai.mlflow")
    private val mlflowClient = MlflowClient("http://127.0.0.1:5000")

    private fun createSpan(spanName: String, invocation: MethodInvocation): Span {
        val spanBuilder = tracer.spanBuilder(spanName)
        val parentSpan = Span.current()
        // If parent exists, set parent
        if (parentSpan.spanContext.isValid) {
            spanBuilder.setParent(Context.current())
        } else {
            spanBuilder.setNoParent()
            // TODO: Do not create new experiment here. Create mlflow service
            val experimentId = mlflowClient.createExperiment(generateRandomString(10))
            val traceCreationInfo = TraceCreationInfo(
                experimentId = experimentId,
                traceCreationPath = invocation.method.declaringClass.name,
                traceName = spanName
            )
            // Get rid of run blocking
            val traceInfoResponse = runBlocking {
                createTrace(traceCreationInfo)
            }
            spanBuilder.setAttribute("traceInfoResponse", traceInfoResponse.toString())
        }
        return spanBuilder.startSpan()
    }

    @Throws(Throwable::class)
    override fun invoke(invocation: MethodInvocation): Any {
        val methodName = invocation.method.name
        val span = createSpan(methodName, invocation)

        invocation.arguments.forEachIndexed { index, argument ->
            span.setAttribute("arg$index", argument.toString())
        }

        val scope: Scope = span.makeCurrent()
        Instant.now().toEpochMilli()
        return try {
            val result = invocation.proceed()
            span.setAttribute("result", result.toString())
            result
        } catch (exception: Throwable) {
            span.recordException(exception)
            span.setStatus(StatusCode.ERROR, exception.message ?: "Message not found")
            throw exception
        } finally {
            Instant.now().toEpochMilli()
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