package ai.dev.kit.tracing.fluent.processor

import kotlin.reflect.KFunction

expect interface SpanData
expect interface SpanBuilder
expect interface Span

expect inline fun <T> withTrace(
    function: KFunction<*>,
    args: Array<Any?>,
    crossinline block: () -> T
): T

expect suspend inline fun <T> withTraceSuspended(
    function: KFunction<*>,
    args: Array<Any?>,
    crossinline block: suspend () -> T
): T
