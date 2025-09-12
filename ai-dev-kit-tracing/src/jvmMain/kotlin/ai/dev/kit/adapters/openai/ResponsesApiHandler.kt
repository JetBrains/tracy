package ai.dev.kit.adapters.openai

import ai.dev.kit.adapters.Url
import io.opentelemetry.api.trace.Span
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_USAGE_INPUT_TOKENS
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_USAGE_OUTPUT_TOKENS
import kotlinx.serialization.json.*

/**
 * Handler for OpenAI Responses API
 */
internal class ResponsesApiHandler : OpenAIApiHandler {

    override fun handleRequestAttributes(span: Span, url: Url, body: JsonObject) {
        OpenAIApiUtils.setCommonRequestAttributes(span, url, body)

        body["input"]?.let { input ->
            if (input is JsonArray) {
                var promptIndex = 0
                for (message in input.jsonArray) {
                    val role = message.jsonObject["role"]?.jsonPrimitive?.content
                    val content = message.jsonObject["content"]
                    span.setAttribute("gen_ai.prompt.$promptIndex.role", role)

                    if (role == "assistant" && content is JsonArray) {
                        val toolResults = content.jsonArray.filter {
                            it.jsonObject["type"]?.jsonPrimitive?.content == "output_text" &&
                                    it.jsonObject["tool_use_id"] != null
                        }

                        if (toolResults.isNotEmpty()) {
                            span.setAttribute("gen_ai.prompt.$promptIndex.content", null)
                            promptIndex++

                            for (toolResult in toolResults) {
                                span.setAttribute("gen_ai.prompt.$promptIndex.role", "tool")
                                span.setAttribute(
                                    "gen_ai.prompt.$promptIndex.content",
                                    toolResult.jsonObject["text"]?.jsonPrimitive?.content
                                )
                                span.setAttribute(
                                    "gen_ai.prompt.$promptIndex.tool_call_id",
                                    toolResult.jsonObject["tool_use_id"]?.jsonPrimitive?.content
                                )
                                promptIndex++
                            }
                        } else {
                            span.setAttribute("gen_ai.prompt.$promptIndex.role", role)
                            span.setAttribute("gen_ai.prompt.$promptIndex.content", content.toString())
                            promptIndex++
                        }
                    } else {
                        if (role == "tool") {
                            span.setAttribute(
                                "gen_ai.prompt.$promptIndex.tool_call_id",
                                message.jsonObject["tool_call_id"]?.jsonPrimitive?.content
                            )
                        }
                        promptIndex++
                    }
                }
            } else {
                span.setAttribute("gen_ai.prompt.0.role", "user")
                span.setAttribute("gen_ai.prompt.0.content", input.toString())
            }
        }

        body["tools"]?.let {
            if (it is JsonArray) {
                for ((index, tool) in it.jsonArray.withIndex()) {
                    span.setAttribute("gen_ai.tool.$index.type", tool.jsonObject["type"]?.jsonPrimitive?.content)

                    tool.jsonObject["name"]?.let {
                        span.setAttribute("gen_ai.tool.$index.name", it.jsonPrimitive.content)
                    }
                    tool.jsonObject["description"]?.let {
                        span.setAttribute("gen_ai.tool.$index.description", it.jsonPrimitive.content)
                    }
                    tool.jsonObject["parameters"]?.let {
                        span.setAttribute("gen_ai.tool.$index.parameters", it.jsonObject.toString())
                    }
                    tool.jsonObject["strict"]?.let {
                        span.setAttribute("gen_ai.tool.$index.strict", it.jsonPrimitive.boolean.toString())
                    }
                }
            }
        }
    }

    override fun handleResponseAttributes(span: Span, body: JsonObject) {
        OpenAIApiUtils.setCommonResponseAttributes(span, body)

        body["output"]?.let { output ->
            val functionCalls =
                output.jsonArray.filter { it.jsonObject["type"]?.jsonPrimitive?.content == "function_call" }
            val messages = output.jsonArray.filter { it.jsonObject["type"]?.jsonPrimitive?.content == "message" }

            if (functionCalls.isNotEmpty()) {
                for ((toolIndex, call) in functionCalls.withIndex()) {
                    span.setAttribute(
                        "gen_ai.completion.0.tool.$toolIndex.call.id",
                        call.jsonObject["call_id"]?.jsonPrimitive?.content
                    )
                    span.setAttribute("gen_ai.completion.0.tool.$toolIndex.call.type", "function")
                    span.setAttribute(
                        "gen_ai.completion.0.tool.$toolIndex.name",
                        call.jsonObject["name"]?.jsonPrimitive?.content
                    )
                    span.setAttribute(
                        "gen_ai.completion.0.tool.$toolIndex.arguments",
                        call.jsonObject["arguments"]?.jsonPrimitive?.content
                    )
                }
                span.setAttribute("gen_ai.completion.0.finish_reason", "tool_calls")
            } else if (messages.isNotEmpty()) {
                for ((index, message) in messages.withIndex()) {
                    span.setAttribute(
                        "gen_ai.completion.$index.role",
                        message.jsonObject["role"]?.jsonPrimitive?.content
                    )

                    message.jsonObject["content"]?.jsonArray?.let { contentArray ->
                        val textContent = contentArray.firstOrNull {
                            it.jsonObject["type"]?.jsonPrimitive?.content == "output_text"
                        }?.jsonObject
                        textContent?.get("text")?.jsonPrimitive?.content?.let {
                            span.setAttribute("gen_ai.completion.$index.content", it)
                        }

                        textContent?.get("annotations")?.let {
                            span.setAttribute("gen_ai.completion.$index.annotations", it.toString())
                        }
                    }

                    span.setAttribute(
                        "gen_ai.completion.$index.finish_reason",
                        message.jsonObject["status"]?.jsonPrimitive?.content
                    )
                }
            }
        }

        body["usage"]?.let { usage ->
            setUsageAttributes(span, usage.jsonObject)
        }
    }

    /**
     * Sets usage attributes (input_tokens/output_tokens)
     */
    private fun setUsageAttributes(span: Span, usage: JsonObject) {
        usage["input_tokens"]?.jsonPrimitive?.intOrNull?.let {
            span.setAttribute(GEN_AI_USAGE_INPUT_TOKENS, it)
        }
        usage["output_tokens"]?.jsonPrimitive?.intOrNull?.let {
            span.setAttribute(GEN_AI_USAGE_OUTPUT_TOKENS, it)
        }
    }
}
