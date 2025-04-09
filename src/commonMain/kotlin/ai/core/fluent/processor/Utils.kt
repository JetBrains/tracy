package ai.core.fluent.processor


import kotlin.reflect.KFunction

expect fun <T> withTrace(
    function: KFunction<*>,
    args: Array<Any?>,
    tracingMetadataConfigurator: TracingMetadataConfigurator,
    block: () -> T
): T

expect suspend fun <T> withTraceSuspended(
    function: KFunction<*>,
    args: Array<Any?>,
    tracingMetadataConfigurator: TracingMetadataConfigurator,
    block: suspend () -> T
): T
