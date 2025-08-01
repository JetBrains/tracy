package ai.dev.kit.example.haiku

import kotlinx.coroutines.runBlocking
import ai.dev.kit.exporters.createLangfuseExporter
import ai.dev.kit.tracing.LangfuseConfig
import ai.dev.kit.tracing.TracingManager
import ai.dev.kit.tracing.fluent.KotlinFlowTrace
import ai.dev.kit.tracing.setupTracing
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.asTools
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import kotlinx.coroutines.delay

@KotlinFlowTrace(name = "HAHAHAHAAHAHHA")
suspend fun callModel(): String {
    val toolRegistry = ToolRegistry {
        tools(CalculatorTools().asTools())
    }

    val agent = AIAgent(
        executor = simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY") ?: error("OPENAI_API_KEY not set")),
        llmModel = OpenAIModels.Chat.GPT4o,
        systemPrompt = "You are a calculator",
        toolRegistry = toolRegistry
    ) {
        handleEvents {
            onToolCall { eventContext ->
                println("Tool called: tool ${eventContext.tool.name}, args ${eventContext.toolArgs}")
            }

            onAgentRunError { eventContext ->
                println("An error occurred: ${eventContext.throwable.message}\n${eventContext.throwable.stackTraceToString()}")
            }

            onAgentFinished { eventContext ->
                println("Result: ${eventContext.result}")
            }
        }
        install(OpenTelemetry) {
            setSdk(TracingManager.sdk)
        }
    }

    return agent.run("(10 + 20) - 11")
}

//@KotlinFlowTrace(name = "Calculator")
fun main(): Unit = runBlocking {
    TracingManager.setup(LangfuseConfig(
        "https://langfuse.labs.jb.gg",
        "pk-lf-ecf43a69-5b7b-43ae-9f78-9d8650dd9169",
        "sk-lf-676d7f86-b82a-4caf-ace0-3db69349242a",
    ))
    callModel().also { println(it) }
    delay(1000)
}