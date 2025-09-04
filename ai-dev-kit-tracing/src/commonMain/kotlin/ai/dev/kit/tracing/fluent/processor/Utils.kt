package ai.dev.kit.tracing.fluent.processor

import ai.dev.kit.tracing.fluent.KotlinFlowTrace
import kotlin.reflect.KFunction

expect interface SpanData
expect interface SpanBuilder
expect interface Span

expect inline fun <T> withTrace(
    function: KFunction<*>,
    args: Array<Any?>,
    traceAnnotation: KotlinFlowTrace,
    crossinline block: () -> T
): T

expect suspend inline fun <T> withTraceSuspended(
    function: KFunction<*>,
    args: Array<Any?>,
    traceAnnotation: KotlinFlowTrace,
    crossinline block: suspend () -> T
): T
