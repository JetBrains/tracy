package org.example.ai.mlflow.fluent.processor

import org.example.ai.mlflow.fluent.attrubute.handlers.FunctionSpanAttributeHandler
import org.example.ai.mlflow.fluent.attrubute.handlers.OpenAiClientAttributeHandler
import java.lang.reflect.Method

enum class SpanAttributeHandlerType(val handler: SpanAttributeHandler) {
    FUNCTION(FunctionSpanAttributeHandler),
    OPEN_AI_CLIENT(OpenAiClientAttributeHandler)
}

interface SpanAttributeHandler {
    fun processInput(method: Method, args: Array<Any?>): String
    fun processOutput(result: Any?): String
}
