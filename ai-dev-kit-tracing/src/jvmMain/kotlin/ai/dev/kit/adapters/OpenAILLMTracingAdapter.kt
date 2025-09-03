package ai.dev.kit.adapters

import ai.dev.kit.adapters.openai.ChatCompletionsHandler
import ai.dev.kit.adapters.openai.ResponsesApiHandler
import io.opentelemetry.api.trace.Span
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GenAiSystemIncubatingValues
import kotlinx.serialization.json.JsonObject


/**
 * Detects which OpenAI API is being used based on the request / response structure
 */
private enum class OpenAIApiType {
    CHAT_COMPLETIONS,
    RESPONSES_API
}

internal class OpenAILLMTracingAdapter : LLMTracingAdapter(genAISystem = GenAiSystemIncubatingValues.OPENAI) {
    private val chatHandler = ChatCompletionsHandler()
    private val responsesHandler = ResponsesApiHandler()

    override fun getRequestBodyAttributes(span: Span, url: Url, body: JsonObject) {
        val handler = when (detectApiType(body, null)) {
            OpenAIApiType.CHAT_COMPLETIONS -> chatHandler
            OpenAIApiType.RESPONSES_API -> responsesHandler
        }

        handler.handleRequestAttributes(span, url, body)
    }

    override fun getResultBodyAttributes(span: Span, body: JsonObject) {
        val handler = when (detectApiType(null, body)) {
            OpenAIApiType.CHAT_COMPLETIONS -> chatHandler
            OpenAIApiType.RESPONSES_API -> responsesHandler
        }

        handler.handleResponseAttributes(span, body)
    }


    /*override fun getRequestBodyAttributes(span: Span, url: Url, body: JsonObject) {
        body["temperature"]?.let { span.setAttribute(GEN_AI_REQUEST_TEMPERATURE, it.jsonPrimitive.content.toDouble()) }
        body["model"]?.let { span.setAttribute(GEN_AI_REQUEST_MODEL, it.jsonPrimitive.content) }

        body["messages"]?.let {
            for ((index, message) in it.jsonArray.withIndex()) {
                val role = message.jsonObject["role"]?.jsonPrimitive?.content
                span.setAttribute("gen_ai.prompt.$index.role", role)
                span.setAttribute("gen_ai.prompt.$index.content", message.jsonObject["content"]?.toString())

                // when a tool result is encountered
                if (role?.lowercase() == "tool") {
                    span.setAttribute("gen_ai.prompt.$index.tool_call_id", message.jsonObject["tool_call_id"]?.jsonPrimitive?.content)
                }
            }
        }

        // See: https://platform.openai.com/docs/api-reference/chat/create
        body["tools"]?.let {
            for ((index, tool) in it.jsonArray.withIndex()) {
                span.setAttribute("gen_ai.tool.$index.type", tool.jsonObject["type"]?.jsonPrimitive?.content)
                tool.jsonObject["function"]?.jsonObject?.let {
                    span.setAttribute("gen_ai.tool.$index.name", it["name"]?.jsonPrimitive?.content)
                    span.setAttribute("gen_ai.tool.$index.description", it["description"]?.jsonPrimitive?.content)
                    span.setAttribute("gen_ai.tool.$index.parameters", it["parameters"]?.jsonObject?.toString())
                    span.setAttribute("gen_ai.tool.$index.strict", it["strict"]?.jsonPrimitive?.boolean.toString())
                }
            }
        }
    }*/

    /*override fun getResultBodyAttributes(span: Span, body: JsonObject) {
        body["id"]?.let { span.setAttribute(GEN_AI_RESPONSE_ID, it.jsonPrimitive.content) }
        body["object"]?.let { span.setAttribute("llm.request.type", it.jsonPrimitive.content) }
        body["model"]?.let { span.setAttribute(GEN_AI_RESPONSE_MODEL, it.jsonPrimitive.content) }

        body["choices"]?.let {
            for ((index, choice) in it.jsonArray.withIndex()) {
                val index = choice.jsonObject["index"]?.jsonPrimitive?.int ?: index

                span.setAttribute(
                    "gen_ai.completion.$index.finish_reason",
                    choice.jsonObject["finish_reason"]?.jsonPrimitive?.content
                )

                choice.jsonObject["message"]?.jsonObject?.let { message ->
                    span.setAttribute(
                        "gen_ai.completion.$index.role",
                        message.jsonObject["role"]?.jsonPrimitive?.content
                    )
                    span.setAttribute("gen_ai.completion.$index.content", message.jsonObject["content"]?.toString())

                    // See: https://platform.openai.com/docs/api-reference/chat/object
                    message.jsonObject["tool_calls"]?.let {
                        // sometimes, this prop is explicitly set to null, hence, being JsonNull.
                        // therefore, we check for the required array type
                        if (it is JsonArray) {
                            for ((toolCallIndex, toolCall) in it.jsonArray.withIndex()) {
                                // gen_ai.tool.call.id
                                span.setAttribute(
                                    "gen_ai.completion.$index.tool.$toolCallIndex.call.id",
                                    toolCall.jsonObject["id"]?.jsonPrimitive?.content
                                )
                                // gen_ai.tool.type
                                span.setAttribute(
                                    "gen_ai.completion.$index.tool.$toolCallIndex.call.type",
                                    toolCall.jsonObject["type"]?.jsonPrimitive?.content
                                )

                                // extract function name and arguments
                                toolCall.jsonObject["function"]?.jsonObject?.let {
                                    // gen_ai.tool.name
                                    span.setAttribute(
                                        "gen_ai.completion.$index.tool.$toolCallIndex.name",
                                        it["name"]?.jsonPrimitive?.content
                                    )
                                    span.setAttribute(
                                        "gen_ai.completion.$index.tool.$toolCallIndex.arguments",
                                        it["arguments"]?.jsonPrimitive?.content
                                    )
                                }
                            }
                        }
                    }

                    span.setAttribute("gen_ai.completion.$index.annotations", message.jsonObject["annotations"].toString())
                }
            }
        }

        body["usage"]?.let { usage ->
            usage.jsonObject["prompt_tokens"]?.jsonPrimitive?.int?.let {
                span.setAttribute(
                    GEN_AI_USAGE_INPUT_TOKENS,
                    it
                )
            }
            usage.jsonObject["completion_tokens"]?.jsonPrimitive?.int?.let {
                span.setAttribute(
                    GEN_AI_USAGE_OUTPUT_TOKENS,
                    it
                )
            }
            // TODO: add other usage attributes
        }
    }*/
}

private fun detectApiType(requestBody: JsonObject?, responseBody: JsonObject?): OpenAIApiType {
    requestBody?.let { body ->
        if (body.containsKey("messages")) return OpenAIApiType.CHAT_COMPLETIONS
        if (body.containsKey("input")) return OpenAIApiType.RESPONSES_API
    }

    responseBody?.let { body ->
        if (body.containsKey("choices")) return OpenAIApiType.CHAT_COMPLETIONS
        if (body.containsKey("output")) return OpenAIApiType.RESPONSES_API
    }

    // Default to chat completions for backwards compatibility
    return OpenAIApiType.CHAT_COMPLETIONS
}
