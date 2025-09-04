package ai.dev.kit.tracing.fluent.processor

import ai.dev.kit.tracing.fluent.KotlinFlowTrace
import kotlin.reflect.KFunction

actual interface SpanData
actual interface SpanBuilder
actual interface Span

actual inline fun <T> withTrace(
    function: KFunction<*>,
    args: Array<Any?>,
    traceAnnotation: KotlinFlowTrace,
    crossinline block: () -> T
): T {
    throw NotImplementedError()
}

actual suspend inline fun <T> withTraceSuspended(
    function: KFunction<*>,
    args: Array<Any?>,
    traceAnnotation: KotlinFlowTrace,
    crossinline block: suspend () -> T
): T {
    throw NotImplementedError()
}
