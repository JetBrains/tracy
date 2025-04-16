package ai.dev.kit.core.fluent.processor

import kotlin.reflect.KFunction

actual interface SpanData
actual interface SpanBuilder
actual interface Span

actual fun <T> withTrace(
    function: KFunction<*>,
    args: Array<Any?>,
    block: () -> T
): T {
    throw NotImplementedError()
}

actual suspend fun <T> withTraceSuspended(
    function: KFunction<*>,
    args: Array<Any?>,
    block: suspend () -> T
): T {
    throw NotImplementedError()
}
