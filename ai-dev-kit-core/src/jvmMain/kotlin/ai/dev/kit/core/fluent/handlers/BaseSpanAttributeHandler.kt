package ai.dev.kit.core.fluent.handlers

import kotlin.coroutines.Continuation
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.jvm.java

actual object BaseSpanAttributeHandler : SpanAttributeHandler {
    actual override fun processInput(method: PlatformMethod, args: Array<Any?>): String {
        val inputJsonObject = buildJsonObject {
            method.parameters
                .filter { it.type != Continuation::class.java } // Exclude Continuation parameters
                .forEachIndexed { index, parameter ->
                    val paramName = parameter.name ?: "arg$index"
                    val argumentValue = args.getOrNull(index)
                    put(paramName, convertToJsonPrimitive(argumentValue))
                }
        }
        return inputJsonObject.toString()
    }

    private fun convertToJsonPrimitive(value: Any?): JsonPrimitive {
        return when (value) {
            null -> JsonPrimitive("null")
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            else -> JsonPrimitive(value.toString())
        }
    }

    actual override fun processOutput(result: Any?): String = result.toString()
}
