package ai.core.fluent.handlers

import org.mlflow_project.google.gson.Gson
import kotlin.coroutines.Continuation
import kotlin.jvm.java

actual object BaseSpanAttributeHandler : SpanAttributeHandler {
    actual override fun processInput(method: PlatformMethod, args: Array<Any?>): String {
        val inputMap = method.parameters
            // Get rid of continuation parameter
            .filter {  it.type != Continuation::class.java }
            .mapIndexed { index, parameter ->
                val paramName = parameter.name ?: "arg$index"
                val argumentValue = args.getOrNull(index) ?: "null"
                paramName to argumentValue
            }.toMap()
        return Gson().toJson(inputMap)
    }

    actual override fun processOutput(result: Any?): String = result.toString()
}
