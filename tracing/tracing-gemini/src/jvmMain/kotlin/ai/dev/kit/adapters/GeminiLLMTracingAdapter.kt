package ai.dev.kit.adapters

import io.opentelemetry.api.trace.Span
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal class GeminiLLMTracingAdapter : LLMTracingAdapter(genAISystem = GenAiIncubatingAttributes.GenAiSystemIncubatingValues.GEMINI) {
    override fun getRequestBodyAttributes(span: Span, url: Url, body: JsonObject) {
        // See: https://ai.google.dev/api/caching#Content
        body["contents"]?.let {
            for ((index, message) in it.jsonArray.withIndex()) {
                span.setAttribute("gen_ai.prompt.$index.role", message.jsonObject["role"]?.jsonPrimitive?.content)
                span.setAttribute("gen_ai.prompt.$index.content", message.jsonObject["parts"].toString())
            }
        }

        // url ends with `[model]:[operation]`
        val (model, operation) = url.pathSegments.lastOrNull()?.split(":")
            ?.let { it.firstOrNull() to it.lastOrNull() } ?: (null to null)

        model?.let { span.setAttribute(GenAiIncubatingAttributes.GEN_AI_REQUEST_MODEL, model) }
        // TODO: use GEN_AI_OPERATION_NAME?
        operation?.let { span.setAttribute("llm.request.type", operation) }

        // extract tool calls
        body.jsonObject["tools"]?.let {
            if (it is JsonArray) {
                for ((index, tool) in it.jsonArray.withIndex()) {
                    tool.jsonObject["functionDeclarations"]?.let {
                        for ((functionIndex, function) in it.jsonArray.withIndex()) {
                            function.jsonObject["parameters"]?.jsonObject?.let {
                                span.setAttribute(
                                    "gen_ai.tool.$index.function.$functionIndex.type",
                                    it["type"]?.jsonPrimitive?.content
                                )
                            }
                            span.setAttribute(
                                "gen_ai.tool.$index.function.$functionIndex.name",
                                function.jsonObject["name"]?.jsonPrimitive?.content
                            )
                            span.setAttribute(
                                "gen_ai.tool.$index.function.$functionIndex.description",
                                function.jsonObject["description"]?.jsonPrimitive?.content
                            )
                            span.setAttribute(
                                "gen_ai.tool.$index.function.$functionIndex.parameters",
                                function.jsonObject["parameters"].toString()
                            )
                        }
                    }
                }
            }
        }

        // See: https://ai.google.dev/api/generate-content#v1beta.GenerationConfig
        body["generationConfig"]?.let {
            it.jsonObject["candidateCount"]?.jsonPrimitive?.intOrNull?.let {
                span.setAttribute(GenAiIncubatingAttributes.GEN_AI_REQUEST_CHOICE_COUNT, it.toLong())
            }
            it.jsonObject["maxOutputTokens"]?.jsonPrimitive?.intOrNull?.let {
                span.setAttribute(GenAiIncubatingAttributes.GEN_AI_REQUEST_MAX_TOKENS, it.toLong())
            }
            it.jsonObject["temperature"]?.jsonPrimitive?.doubleOrNull?.let { span.setAttribute(GenAiIncubatingAttributes.GEN_AI_REQUEST_TEMPERATURE, it) }
            it.jsonObject["topP"]?.jsonPrimitive?.doubleOrNull?.let { span.setAttribute(GenAiIncubatingAttributes.GEN_AI_REQUEST_TOP_P, it) }
            it.jsonObject["topK"]?.jsonPrimitive?.doubleOrNull?.let { span.setAttribute(GenAiIncubatingAttributes.GEN_AI_REQUEST_TOP_K, it) }
        }
    }

    override fun getResultBodyAttributes(span: Span, body: JsonObject) {
        // See: https://ai.google.dev/api/generate-content#v1beta.GenerateContentResponse
        body["responseId"]?.let { span.setAttribute(GenAiIncubatingAttributes.GEN_AI_RESPONSE_ID, it.jsonPrimitive.content) }
        body["modelVersion"]?.let { span.setAttribute(GenAiIncubatingAttributes.GEN_AI_RESPONSE_MODEL, it.jsonPrimitive.content) }

        body["candidates"]?.let {
            for ((index, candidate) in it.jsonArray.withIndex()) {
                candidate.jsonObject["content"]?.let { content ->
                    span.setAttribute(
                        "gen_ai.completion.$index.role",
                        content.jsonObject["role"]?.jsonPrimitive?.content
                    )
                    // response parts
                    val parts = content.jsonObject["parts"]
                    span.setAttribute("gen_ai.completion.$index.content", parts.toString())

                    // collect requests for a tool call
                    if (parts is JsonArray) {
                        var toolCallIndex = 0
                        for (part in parts.jsonArray) {
                            part.jsonObject["functionCall"]?.jsonObject?.let {
                                span.setAttribute(
                                    "gen_ai.completion.$index.tool.$toolCallIndex.name",
                                    it["name"]?.jsonPrimitive?.content
                                )
                                span.setAttribute(
                                    "gen_ai.completion.$index.tool.$toolCallIndex.arguments",
                                    it["args"].toString()
                                )
                                ++toolCallIndex
                            }
                        }
                    }
                }

                span.setAttribute(
                    "gen_ai.completion.$index.finish_reason",
                    candidate.jsonObject["finishReason"]?.jsonPrimitive?.content
                )
            }
        }

        body["usageMetadata"]?.let { usage ->
            usage.jsonObject["promptTokenCount"]?.jsonPrimitive?.intOrNull?.let {
                span.setAttribute(GenAiIncubatingAttributes.GEN_AI_USAGE_INPUT_TOKENS, it)
            }
            usage.jsonObject["candidatesTokenCount"]?.jsonPrimitive?.intOrNull?.let {
                span.setAttribute(GenAiIncubatingAttributes.GEN_AI_USAGE_OUTPUT_TOKENS, it)
            }
            usage.jsonObject["totalTokenCount"]?.jsonPrimitive?.intOrNull?.let {
                span.setAttribute("gen_ai.usage.total_tokens", it.toLong())
            }

            // TODO: think about the mapping of the below properties (see: https://github.com/JetBrains/ai-dev-kit/pull/54#discussion_r2229750741)
            /**
             * The following two properties (`promptTokensDetails`, `candidatesTokensDetails`)
             * and their inner contents are mapped into snake-cased OTEL attributes.
             *
             * 1. For `promptTokensDetails`:
             *   - `"gen_ai.usage.prompt_tokens_details.0.modality"`
             *   - `"gen_ai.usage.prompt_tokens_details.0.token_count"`
             * 2. For `candidatesTokensDetails`:
             *   - `"gen_ai.usage.candidates_tokens_details.0.modality"`
             *   - `"gen_ai.usage.candidates_tokens_details.0.token_count"`
             *
             * See: https://ai.google.dev/api/generate-content#UsageMetadata
             */
            // prompt tokens details
            extractUsageTokenDetails(span, usage, attribute = "promptTokensDetails")
            // candidate tokens details
            extractUsageTokenDetails(span, usage, attribute = "candidatesTokensDetails")
        }
    }

    private fun extractUsageTokenDetails(span: Span, usage: JsonElement, attribute: String) {
        // turn the given attribute into snake-cased format
        val snakeCasedAttribute = attribute.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()

        usage.jsonObject[attribute]?.let {
            for ((index, detail) in it.jsonArray.withIndex()) {
                detail.jsonObject["modality"]?.let {
                    span.setAttribute("gen_ai.usage.$snakeCasedAttribute.$index.modality", it.jsonPrimitive.content)
                }
                detail.jsonObject["tokenCount"]?.jsonPrimitive?.intOrNull?.let {
                    span.setAttribute("gen_ai.usage.$snakeCasedAttribute.$index.token_count", it.toLong())
                }
            }
        }
    }
}