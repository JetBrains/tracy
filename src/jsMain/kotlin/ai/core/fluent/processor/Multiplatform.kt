package ai.core.fluent.processor


import kotlin.reflect.KFunction

actual interface SpanBuilder
actual interface Span

actual fun <T> withTrace(
    function: KFunction<*>,
    args: Array<Any?>,
    tracingMetadataConfigurator: TracingMetadataConfigurator,
    block: () -> T
): T {
    throw UnsupportedOperationException("Not yet implemented")
}

actual suspend fun <T> withTraceSuspended(
    function: KFunction<*>,
    args: Array<Any?>,
    tracingMetadataConfigurator: TracingMetadataConfigurator,
    block: suspend () -> T
): T {
    throw UnsupportedOperationException("Not yet implemented")
}
