package org.example.ai.mlflow.fluent.processor

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.TracerProvider
import io.opentelemetry.context.Context
import io.opentelemetry.context.Scope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.example.ai.mlflow.KotlinMlflowClient
import org.example.ai.mlflow.createTrace
import org.example.ai.mlflow.dataclasses.TraceInfo
import org.example.ai.mlflow.dataclasses.createTracePostRequest
import org.example.ai.mlflow.fluent.FluentSpanAttributes
import org.example.ai.mlflow.fluent.KotlinFlowTrace
//import org.example.ai.mlflow.fluent.processor.TracedMethodInterceptor.tracer
import java.lang.reflect.Method
import kotlin.jvm.java

suspend fun <T> withTraceSuspended(spanName: String, block: suspend () -> T): T {
//    val span: Span = TracedMethodInterceptor.createSpan()
    return block()
}

fun <T> withTrace(spanName: String, block: () -> T): T {
    // Create a new tracer instance
    val tracer = GlobalOpenTelemetry.getTracer("org.example.ai.mlflow")

    // Start a new span
    val span: Span = tracer.spanBuilder(spanName).startSpan()
//    val context = span.storeInContext()
//
//    // Open a scope so the context is applied correctly
//    @Suppress("TooGenericExceptionCaught")
//    return try {
//        withContext(context.asContextElement()) {
//            block() // Invoke the block with the span context
//        }
//    } catch (exception: Throwable) {
//        // Record exception in the span
//        span.recordException(exception)
//        span.setStatus(io.opentelemetry.api.common.AttributeKey.stringKey("status.code"), "ERROR")
//
//        // Rethrow the exception
//        throw exception
//    } finally {
//        // Close the span (end tracing)
//        span.end()
//    }
    return block()
}

