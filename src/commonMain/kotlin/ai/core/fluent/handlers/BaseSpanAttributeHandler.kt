package ai.core.fluent.handlers

expect class BaseSpanAttributeHandler : SpanAttributeHandler {
    override fun processInput(method: PlatformMethod, args: Array<Any?>): String
    override fun processOutput(result: Any?): String
}
