package ai.core.fluent.handlers

interface SpanAttributeHandler {
    fun processInput(method: PlatformMethod, args: Array<Any?>): String
    fun processOutput(result: Any?): String
}

expect class PlatformMethod
