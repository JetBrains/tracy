package ai.dev.kit.core.fluent.processor

import kotlin.reflect.KFunction

actual interface SpanData
actual interface SpanBuilder
actual interface Span

actual inline fun <T> withTrace(
    function: KFunction<*>,
    args: Array<Any?>,
    crossinline block: () -> T
): T {
    throw NotImplementedError()
}

actual suspend inline fun <T> withTraceSuspended(
    function: KFunction<*>,
    args: Array<Any?>,
    crossinline block: suspend () -> T
): T {
    throw NotImplementedError()
}
