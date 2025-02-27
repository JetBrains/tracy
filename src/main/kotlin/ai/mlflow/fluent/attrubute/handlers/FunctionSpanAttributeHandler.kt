package org.example.ai.mlflow.fluent.attrubute.handlers

import org.example.ai.mlflow.fluent.processor.SpanAttributeHandler
import java.lang.reflect.Method

object FunctionSpanAttributeHandler : SpanAttributeHandler {
    override fun processInput(method: Method, args: Array<Any?>): String = method.parameters
        .mapIndexed { index, parameter ->
            val paramName = parameter.name ?: "arg$index"
            val argumentValue = args.getOrNull(index)?.toString() ?: "null"
            "\"$paramName\": $argumentValue"
        }
        .joinToString(", ", prefix = "{", postfix = "}")

    override fun processOutput(result: Any?): String = result.toString()
}
