package ai.core.fluent.handlers


actual object BaseSpanAttributeHandler : SpanAttributeHandler {
    actual override fun processInput(method: PlatformMethod, args: Array<Any?>): String =
        throw UnsupportedOperationException("Not yet implemented")

    actual override fun processOutput(result: Any?): String =
        throw UnsupportedOperationException("Not yet implemented")
}
