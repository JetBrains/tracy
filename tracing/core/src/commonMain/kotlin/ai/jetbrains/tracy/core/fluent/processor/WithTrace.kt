package ai.jetbrains.tracy.core.fluent.processor

import ai.jetbrains.tracy.core.fluent.Trace
import kotlin.reflect.KFunction

/**
 * Executes the given [block] within a tracing span.
 *
 * This function is generated and invoked by the Tracy compiler plugin
 * when transforming functions annotated with [Trace].
 *
 * This function is not intended to be called directly.
 */
expect inline fun <T> withTrace(
    function: KFunction<*>,
    args: Array<Any?>,
    traceAnnotation: Trace,
    crossinline block: () -> T
): T

/**
 * Executes the given suspend [block] within a tracing span.
 *
 * This function is generated and invoked by the Tracy compiler plugin
 * when transforming suspend functions annotated with [Trace].
 *
 * This function is not intended to be called directly.
 */
expect suspend inline fun <T> withTraceSuspended(
    function: KFunction<*>,
    args: Array<Any?>,
    traceAnnotation: Trace,
    crossinline block: suspend () -> T
): T
