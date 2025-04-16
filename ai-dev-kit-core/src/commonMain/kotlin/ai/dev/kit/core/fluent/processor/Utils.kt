package ai.dev.kit.core.fluent.processor

import kotlin.reflect.KFunction

expect fun <T> withTrace(
    function: KFunction<*>,
    args: Array<Any?>,
    block: () -> T
): T

expect suspend fun <T> withTraceSuspended(
    function: KFunction<*>,
    args: Array<Any?>,
    block: suspend () -> T
): T
