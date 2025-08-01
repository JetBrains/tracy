package ai.dev.kit.example.haiku

import ai.dev.kit.tracing.fluent.KotlinFlowTrace
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet

@KotlinFlowTrace
fun traceableFunction(param: String): String {
    return "$param!!!"
}

@Suppress("unused")
@LLMDescription("Tools for basic calculator operations")
class CalculatorTools : ToolSet {
    @Tool
    @LLMDescription("Adds two numbers")
    fun plus(
        @LLMDescription("First number")
        a: Float,

        @LLMDescription("Second number")
        b: Float
    ): String {
        traceableFunction("RandomParam1")
        return (a + b).toString()
    }

    @Tool
    @LLMDescription("Subtracts the second number from the first")
    fun minus(
        @LLMDescription("First number")
        a: Float,

        @LLMDescription("Second number")
        b: Float
    ): String {
        traceableFunction("RandomParam2")
        return (a - b).toString()
    }
}