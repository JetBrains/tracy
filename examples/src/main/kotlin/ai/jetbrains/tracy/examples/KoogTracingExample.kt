package ai.jetbrains.tracy.examples

import ai.jetbrains.tracy.core.exporters.ConsoleExporterConfig
import ai.jetbrains.tracy.core.tracing.TracingManager
import ai.jetbrains.tracy.core.tracing.configureOpenTelemetrySdk
import ai.jetbrains.tracy.core.fluent.Trace
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.agents.core.tools.reflect.asTools
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor

/**
 * Example [ToolSet] that defines a simple sorting tool for use with a [Koog](https://github.com/koog-ai/koog) [AIAgent].
 *
 * The tools in this set are annotated with [Trace] to automatically generate tracing spans
 * for both tool invocation and helper function.
 */
object SortTools : ToolSet {
    @Tool
    @LLMDescription("Sorts a comma-separated list of integers in ascending order and returns the sorted list as a string.")
    @Trace(name = "Sort integers")
    fun sortIntegers(
        @LLMDescription("Comma-separated integers, e.g. '1,2,3,4,5'") numbers: String
    ): String = parseCsvInts(numbers).sorted().joinToString(",")

    @Trace(name = "Parse comma-separated list")
    private fun parseCsvInts(input: String): List<Int> =
        input.split(',').map { it.trim() }.map { it.toIntOrNull() ?: error("Invalid integer value: '$it'") }
}

/**
 * Example of integrating a [Koog](https://github.com/koog-ai/koog) [AIAgent] with tracing using [Trace].
 *
 * This example demonstrates how to:
 * - Initialize tracing using [TracingManager] with [ConsoleExporterConfig].
 * - Annotate Koog [ToolSet] functions with [Trace] to automatically generate spans for tool calls.
 * - Run an [AIAgent] that discovers and executes annotated tools, capturing detailed trace data for both tool and helper functions.
 *
 * To run this example:
 * * Set the `OPENAI_API_KEY` environment variable to your OpenAI API key.
 *
 * Run the example. The agent will call the `sortIntegers` tool when asked to "Sort 9,1,4,3,7".
 * View trace spans in the console, including both the **Sort integers** tool span and the nested **Parse comma-separated list** span.
 */
suspend fun main() {
    TracingManager.setSdk(configureOpenTelemetrySdk(ConsoleExporterConfig()))
    val apiToken = System.getenv("OPENAI_API_KEY") ?: error("Environment variable 'OPENAI_API_KEY' is not set")
    val agent = AIAgent(
        promptExecutor = simpleOpenAIExecutor(apiToken = apiToken),
        llmModel = OpenAIModels.Chat.GPT4o,
        systemPrompt = "You are a helpful assistant. Answer user questions concisely. Use tools.",
        toolRegistry = ToolRegistry {
            tools(SortTools.asTools())
        }) {
        handleEvents {
            onToolCallStarting {
                println("Tool called: ${it.tool.name}")
            }
        }
    }
    val result = agent.run("Sort 9,1,4,3,7")
    println("Result: $result\nSee trace details in the console.")
    // Manual flush - alternatively, configure automatic flushing via ExporterCommonSettings
    TracingManager.flushTraces()
}