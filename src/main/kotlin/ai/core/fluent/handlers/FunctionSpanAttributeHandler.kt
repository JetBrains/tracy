package ai.core.fluent.handlers

import org.mlflow_project.google.gson.Gson
import java.lang.reflect.Method
import kotlin.coroutines.Continuation
import kotlin.jvm.java

object FunctionSpanAttributeHandler : SpanAttributeHandler {
    override fun processInput(method: Method, args: Array<Any?>): String {
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

    override fun processOutput(result: Any?): String = result.toString()
}
