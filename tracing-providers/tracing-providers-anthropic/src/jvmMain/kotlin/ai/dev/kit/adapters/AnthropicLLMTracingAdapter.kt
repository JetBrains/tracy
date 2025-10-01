package ai.dev.kit.adapters

import io.opentelemetry.api.trace.Span
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GenAiSystemIncubatingValues
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_OUTPUT_TYPE
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_MAX_TOKENS
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_MODEL
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_TEMPERATURE
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_TOP_K
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_TOP_P
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_RESPONSE_FINISH_REASONS
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_RESPONSE_ID
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_RESPONSE_MODEL
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_USAGE_INPUT_TOKENS
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_USAGE_OUTPUT_TOKENS
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class AnthropicLLMTracingAdapter : LLMTracingAdapter(genAISystem = GenAiSystemIncubatingValues.ANTHROPIC) {
    override fun getRequestBodyAttributes(span: Span, url: Url, body: JsonObject) {
        body["temperature"]?.jsonPrimitive?.let { span.setAttribute(GEN_AI_REQUEST_TEMPERATURE, it.doubleOrNull) }
        body["model"]?.jsonPrimitive?.let { span.setAttribute(GEN_AI_REQUEST_MODEL, it.content) }
        body["max_tokens"]?.jsonPrimitive?.intOrNull?.let { span.setAttribute(GEN_AI_REQUEST_MAX_TOKENS, it.toLong()) }

        // metadata
        body["metadata"]?.jsonObject?.let { metadata ->
            metadata["user_id"]?.jsonPrimitive?.let { span.setAttribute("gen_ai.metadata.user_id", it.content) }
        }
        body["service_tier"]?.jsonPrimitive?.let {
            span.setAttribute("gen_ai.usage.service_tier", it.content)
        }

        // system prompt
        body["system"]?.jsonObject?.let { system ->
            system["text"]?.jsonPrimitive?.let { span.setAttribute("gen_ai.prompt.system.content", it.content) }
            system["type"]?.jsonPrimitive?.let { span.setAttribute("gen_ai.prompt.system.type", it.content) }
        }

        body["top_k"]?.jsonPrimitive?.doubleOrNull?.let { span.setAttribute(GEN_AI_REQUEST_TOP_K, it) }
        body["top_p"]?.jsonPrimitive?.doubleOrNull?.let { span.setAttribute(GEN_AI_REQUEST_TOP_P, it) }

        body["messages"]?.let {
            if (it is JsonArray) {
                for ((index, message) in it.jsonArray.withIndex()) {
                    span.setAttribute("gen_ai.prompt.$index.role", message.jsonObject["role"]?.jsonPrimitive?.content)
                    span.setAttribute("gen_ai.prompt.$index.content", message.jsonObject["content"]?.toString())
                }
            }
        }

        // extracting definitions of tool calls
        // see: https://docs.anthropic.com/en/api/messages#body-tools
        body["tools"]?.let {
            if (it is JsonArray) {
                for ((index, tool) in it.jsonArray.withIndex()) {
                    span.setAttribute("gen_ai.tool.$index.name", tool.jsonObject["name"]?.jsonPrimitive?.content)
                    span.setAttribute("gen_ai.tool.$index.description", tool.jsonObject["description"]?.jsonPrimitive?.content)
                    span.setAttribute("gen_ai.tool.$index.type", tool.jsonObject["type"]?.jsonPrimitive?.content)
                    span.setAttribute("gen_ai.tool.$index.parameters", tool.jsonObject["input_schema"].toString())
                }
            }
        }
    }

    override fun getResultBodyAttributes(span: Span, body: JsonObject) {
        body["id"]?.let { span.setAttribute(GEN_AI_RESPONSE_ID, it.jsonPrimitive.content) }
        // TODO: use `llm.request.type`?
        body["type"]?.let { span.setAttribute(GEN_AI_OUTPUT_TYPE, it.jsonPrimitive.content) }
        body["role"]?.let { span.setAttribute("gen_ai.response.role", it.jsonPrimitive.content) }
        body["model"]?.let { span.setAttribute(GEN_AI_RESPONSE_MODEL, it.jsonPrimitive.content) }

        // collecting response messages
        body["content"]?.let {
            for ((index, message) in it.jsonArray.withIndex()) {
                val type = message.jsonObject["type"]?.jsonPrimitive?.content
                span.setAttribute("gen_ai.completion.$index.type", type)

                if (type == "text") {
                    // normal text message
                    span.setAttribute("gen_ai.completion.$index.content", message.jsonObject["text"]?.toString())
                }
                else if (type == "tool_use") {
                    // tool call request by LLM
                    val toolCall = message
                    // gen_ai.tool.call.id
                    span.setAttribute(
                        "gen_ai.completion.$index.tool.call.id",
                        toolCall.jsonObject["id"]?.jsonPrimitive?.content
                    )
                    // gen_ai.tool.type
                    span.setAttribute(
                        "gen_ai.completion.$index.tool.call.type",
                        toolCall.jsonObject["type"]?.jsonPrimitive?.content
                    )
                    // gen_ai.tool.name
                    span.setAttribute(
                        "gen_ai.completion.$index.tool.name",
                        toolCall.jsonObject["name"]?.jsonPrimitive?.content
                    )
                    span.setAttribute(
                        "gen_ai.completion.$index.tool.arguments",
                        toolCall.jsonObject["input"].toString()
                    )
                }
                else {
                    span.setAttribute("gen_ai.completion.$index.content", message.toString())
                }
            }
        }

        // finish reason
        body["stop_reason"]?.let {
            span.setAttribute(GEN_AI_RESPONSE_FINISH_REASONS, listOf(it.jsonPrimitive.content))
        }

        // collecting usage stats (e.g., input/output tokens)
        body["usage"]?.jsonObject?.let { usage ->
            usage["input_tokens"]?.jsonPrimitive?.intOrNull?.let {
                span.setAttribute(GEN_AI_USAGE_INPUT_TOKENS, it)
            }
            usage["output_tokens"]?.jsonPrimitive?.intOrNull?.let {
                span.setAttribute(GEN_AI_USAGE_OUTPUT_TOKENS, it)
            }
            usage["cache_creation_input_tokens"]?.jsonPrimitive?.intOrNull?.let {
                span.setAttribute("gen_ai.usage.cache_creation_input_tokens", it.toLong())
            }
            usage["cache_read_input_tokens"]?.jsonPrimitive?.intOrNull?.let {
                span.setAttribute("gen_ai.usage.cache_read_input_tokens", it.toLong())
            }
            usage["service_tier"]?.jsonPrimitive?.let {
                span.setAttribute("gen_ai.usage.service_tier", it.content)
            }
        }
    }
}