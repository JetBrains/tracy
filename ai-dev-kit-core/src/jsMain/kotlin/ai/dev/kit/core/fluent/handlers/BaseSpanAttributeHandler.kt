package ai.dev.kit.core.fluent.handlers

actual object BaseSpanAttributeHandler : SpanAttributeHandler {
    actual override fun processInput(method: PlatformMethod, args: Array<Any?>): String =
        throw NotImplementedError()

    actual override fun processOutput(result: Any?): String =
        throw NotImplementedError()
}
