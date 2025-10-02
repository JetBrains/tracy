package ai.dev.kit.adapters.openai

import ai.dev.kit.adapters.Url
import io.opentelemetry.api.trace.Span
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.*
import kotlinx.serialization.json.*

/**
 * Handler for OpenAI Responses API
 */
internal class ResponsesApiHandler : OpenAIApiHandler {

    override fun handleRequestAttributes(span: Span, url: Url, body: JsonObject) {
        OpenAIApiUtils.setCommonRequestAttributes(span, url, body)

        body["previous_response_id"]?.jsonPrimitive?.contentOrNull?.let {
            span.setAttribute("gen_ai.request.previous_response_id", it)
        }
        body["store"]?.jsonPrimitive?.booleanOrNull?.let {
            span.setAttribute("gen_ai.request.store", it)
        }
        body["top_p"]?.jsonPrimitive?.doubleOrNull?.let {
            span.setAttribute(GEN_AI_REQUEST_TOP_P, it)
        }
        body["max_output_tokens"]?.jsonPrimitive?.longOrNull?.let {
            span.setAttribute(GEN_AI_REQUEST_MAX_TOKENS, it)
        }
        body["truncation"]?.jsonPrimitive?.contentOrNull?.let {
            span.setAttribute("gen_ai.request.truncation", it)
        }
        body["parallel_tool_calls"]?.jsonPrimitive?.booleanOrNull?.let {
            span.setAttribute("gen_ai.request.parallel_tool_calls", it)
        }
        body["stream"]?.jsonPrimitive?.booleanOrNull?.let {
            span.setAttribute("gen_ai.request.stream", it)
        }
        body["response_format"]?.jsonPrimitive?.contentOrNull?.let {
            span.setAttribute(GEN_AI_OUTPUT_TYPE, it)
        }
        body["tool_choice"]?.let {
            val content = if (it is JsonPrimitive) it.content else it.toString()
            span.setAttribute("gen_ai.request.tool_choice", content)
        }
        body["reasoning"]?.let {
            span.setAttribute("gen_ai.request.reasoning", it.toString())
        }
        body["text"]?.let {
            span.setAttribute("gen_ai.request.text", it.toString())
        }

        body["input"]?.let { input ->
            var promptIndex = 0
            if (input is JsonArray) {
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
                        } else {
                            span.setAttribute("gen_ai.prompt.$promptIndex.content", content.toString())
                        }
                        promptIndex++
                    }
                }
            } else {
                span.setAttribute("gen_ai.prompt.0.role", "user")
                span.setAttribute("gen_ai.prompt.0.content", input.toString())
                promptIndex++
            }

            body["instructions"]?.jsonPrimitive?.let {
                span.setAttribute("gen_ai.prompt.$promptIndex.content", it.toString())
                span.setAttribute("gen_ai.prompt.$promptIndex.role", "system")
            }
        }

        body["tools"]?.let { tools ->
            if (tools is JsonArray) {
                for ((index, tool) in tools.jsonArray.withIndex()) {
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

        body["output"]?.let { outputs ->
            for ((index, output) in outputs.jsonArray.withIndex()) {
                when (output.jsonObject["type"]?.jsonPrimitive?.content) {
                    "function_call" -> {
                        output.jsonObject["call_id"]?.jsonPrimitive?.content?.let {
                            span.setAttribute("gen_ai.completion.$index.tool_call_id", it)
                        }
                        output.jsonObject["type"]?.jsonPrimitive?.content?.let {
                            span.setAttribute("gen_ai.completion.$index.tool_call_type", it)
                        }
                        output.jsonObject["name"]?.jsonPrimitive?.content?.let {
                            span.setAttribute("gen_ai.completion.$index.tool_name", it)
                        }
                        output.jsonObject["arguments"]?.jsonPrimitive?.content?.let {
                            span.setAttribute("gen_ai.completion.$index.tool_arguments", it)
                        }
                    }

                    "message" -> {
                        output.jsonObject["role"]?.jsonPrimitive?.content?.let {
                            span.setAttribute("gen_ai.completion.$index.role", it)
                        }

                        output.jsonObject["content"]?.jsonArray?.let { contentArray ->
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
                        output.jsonObject["status"]?.jsonPrimitive?.content?.let {
                            span.setAttribute("gen_ai.completion.$index.finish_reason", it)
                        }
                    }

                    "reasoning" -> {
                        // "type" attribute is not rendered on Langfuse
                        output.jsonObject["type"]?.jsonPrimitive?.content?.let {
                            span.setAttribute("gen_ai.completion.$index.output_type", it)
                        }
                        output.jsonObject["id"]?.jsonPrimitive?.content?.let {
                            span.setAttribute("gen_ai.completion.$index.id", it)
                        }
                        output.jsonObject["encrypted_content"]?.jsonPrimitive?.content?.let {
                            span.setAttribute("gen_ai.completion.$index.encrypted_content", it)
                        }
                        output.jsonObject["status"]?.jsonPrimitive?.content?.let {
                            span.setAttribute("gen_ai.completion.$index.status", it)
                        }
                        output.jsonObject["summary"]?.jsonArray?.toString()?.let {
                            span.setAttribute("gen_ai.completion.$index.summary", it)
                        }

                        // content = null breaks rendering on Langfuse
                        output.jsonObject["content"]?.jsonPrimitive?.content?.let {
                            span.setAttribute("gen_ai.completion.$index.output_content", it)
                        }
                    }
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
