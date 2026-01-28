package ai.jetbrains.tracy.core.fluent.processor

import ai.jetbrains.tracy.core.fluent.Trace
import kotlin.reflect.KFunction

expect interface SpanData
expect interface SpanBuilder
expect interface Span

expect inline fun <T> withTrace(
    function: KFunction<*>,
    args: Array<Any?>,
    traceAnnotation: Trace,
    crossinline block: () -> T
): T

expect suspend inline fun <T> withTraceSuspended(
    function: KFunction<*>,
    args: Array<Any?>,
    traceAnnotation: Trace,
    crossinline block: suspend () -> T
): T
