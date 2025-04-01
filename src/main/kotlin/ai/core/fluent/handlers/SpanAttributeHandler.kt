package ai.core.fluent.handlers

import java.lang.reflect.Method

interface SpanAttributeHandler {
    fun processInput(method: Method, args: Array<Any?>): String
    fun processOutput(result: Any?): String
}
