package org.example.ai.mlflow.fluent

import com.google.inject.AbstractModule
import com.google.inject.BindingAnnotation
import com.google.inject.matcher.Matchers
import kotlinx.coroutines.runBlocking
import org.aopalliance.intercept.MethodInterceptor
import org.aopalliance.intercept.MethodInvocation
import org.example.ai.mlflow.trace
import org.mlflow.tracking.MlflowClient


@BindingAnnotation
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME) // required for Guice
annotation class KotlinFlowTrace

fun generateRandomString(length: Int = 10): String {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    return (1..length).map { chars.random() }.joinToString("")
}

data class Argument(
    val name: String, val value: Any
)

fun formatArgumentsAsJson(arguments: List<Argument>): String {
    return arguments.joinToString(
        prefix = "{",
        postfix = "}",
        separator = ", "
    ) { "\"${it.name}\": ${it.value}" }
}


data class TraceInfo(
    val path: String, val methodName: String, val arguments: List<Argument>, val result: Any
)

class KotlinFlowTracer : MethodInterceptor {
    private val mlflowClient = MlflowClient("http://127.0.0.1:5000")

    @Throws(Throwable::class)
    override fun invoke(invocation: MethodInvocation): Any {
        val methodName = invocation.method.name
        println("Starting MLflow run for method: $methodName")

        // Start a new MLflow run
        val experimentId = mlflowClient.createExperiment(generateRandomString(10))

        val result = invocation.proceed()

        runBlocking {
            val arguments = buildList {
                invocation.arguments.forEachIndexed { index, arg ->
                    add(Argument("arg$index", arg))
                }
            }

            trace(
                experimentId, traceInfo = TraceInfo(
                    path = invocation.method.declaringClass.name,
                    methodName = invocation.method.name,
                    arguments = arguments,
                    result = result
                )
            )
        }
        return result
    }
}

class KotlinFlowTraceModule : AbstractModule() {
    override fun configure() {
        bindInterceptor(
            Matchers.any(), Matchers.annotatedWith(KotlinFlowTrace::class.java), KotlinFlowTracer()
        )
    }
}
