/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package ai.jetbrains.tracy.examples

import ai.jetbrains.tracy.core.TracingManager
import ai.jetbrains.tracy.core.configureOpenTelemetrySdk
import ai.jetbrains.tracy.core.currentSpanContext
import ai.jetbrains.tracy.core.currentSpanContextElement
import ai.jetbrains.tracy.core.exporters.ConsoleExporterConfig
import ai.jetbrains.tracy.core.fluent.Trace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.concurrent.thread

@Trace
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
@Trace
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
@Trace
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
@Trace
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
    TracingManager.isTracingEnabled = true
    TracingManager.setSdk(configureOpenTelemetrySdk(ConsoleExporterConfig()))
    runBlocking {
        handleRequestInCoroutine("REQ-101")
        handleRequestWithContext("REQ-202")
        handleRequestInNewThread("REQ-303")
    }
    // Manual flush - alternatively, configure automatic flushing via ExporterCommonSettings
    TracingManager.flushTraces()
}
