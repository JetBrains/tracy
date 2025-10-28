package ai.dev.kit.examples

import ai.dev.kit.exporters.ConsoleExporterConfig
import ai.dev.kit.tracing.TracingManager
import ai.dev.kit.tracing.configureOpenTelemetrySdk
import ai.dev.kit.tracing.fluent.KotlinFlowTrace
import ai.dev.kit.tracing.fluent.processor.currentSpanContext
import ai.dev.kit.tracing.fluent.processor.currentSpanContextElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.concurrent.thread

@KotlinFlowTrace
fun processUserRequest(requestId: String) {
    println("Processing request: $requestId")
}

/**
 * Demonstrates automatic context propagation with structured coroutines.
 *
 * When switching to another dispatcher using [withContext], the OpenTelemetry context
 * is preserved automatically. The nested span created inside `withContext` is linked correctly
 * to its parent span.
 */
@KotlinFlowTrace
suspend fun handleRequestWithContext(requestId: String) {
    println("Running on thread: ${Thread.currentThread().name}")
    withContext(Dispatchers.IO) {
        println("Running inside nested coroutine on thread: ${Thread.currentThread().name}")
        processUserRequest(requestId)
    }
}

/**
 * Demonstrates context propagation when using [runBlocking] inside a suspend function.
 *
 * If [runBlocking] is used without explicitly passing the OpenTelemetry context,
 * the parent span becomes detached, creating separate traces.
 *
 * To preserve span hierarchy, attach the current context using [currentSpanContextElement].
 * This ensures that child spans remain linked to the parent span.
 *
 * Note: [withContext] handles propagation automatically, so no manual setup is needed there.
 */
@KotlinFlowTrace
suspend fun handleRequestInCoroutine(requestId: String) {
    println("Running on thread: ${Thread.currentThread().name}")
    runBlocking(currentSpanContextElement(currentCoroutineContext())) {
        println("Running inside nested coroutine on thread: ${Thread.currentThread().name}")
        processUserRequest(requestId)
    }
}

/**
 * Demonstrates manual context propagation across threads.
 *
 * Threads do not automatically inherit the OpenTelemetry context.
 * To maintain a correct trace hierarchy, capture the current span context
 * using [currentSpanContext] and explicitly activate it inside the new thread.
 *
 * This ensures that spans created within the thread remain part of the same trace.
 */
@KotlinFlowTrace
suspend fun handleRequestInNewThread(requestId: String) {
    println("Running on thread: ${Thread.currentThread().name}")
    val context = currentSpanContext(currentCoroutineContext())
    thread(name = "RequestWorker-$requestId") {
        context.makeCurrent().use {
            processUserRequest(requestId)
        }
    }.join()
}

/**
 * Demonstrates context propagation across different coroutine and threading models.
 *
 * This example shows how OpenTelemetry tracing context flows automatically in structured coroutines
 * (e.g., [withContext]) but requires manual propagation in certain concurrency models such as
 * [runBlocking] and raw threads.
 */
fun main() {
    TracingManager.setSdk(configureOpenTelemetrySdk(ConsoleExporterConfig()))
    runBlocking {
        handleRequestInCoroutine("REQ-101")
        handleRequestWithContext("REQ-202")
        handleRequestInNewThread("REQ-303")
    }
    TracingManager.flushTraces()
}
