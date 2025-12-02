package ai.dev.kit.adapters

import ai.dev.kit.adapters.LLMTracingAdapter.Companion.PayloadType
import ai.dev.kit.http.protocol.Request
import ai.dev.kit.http.protocol.Response
import ai.dev.kit.http.protocol.Url
import ai.dev.kit.http.protocol.asJson
import io.opentelemetry.api.trace.Span
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.*
import kotlinx.serialization.json.*

class GeminiLLMTracingAdapter : LLMTracingAdapter(genAISystem = GenAiSystemIncubatingValues.GEMINI) {
    override fun getRequestBodyAttributes(span: Span, request: Request) {
        // See: https://ai.google.dev/api/caching#Content
        val body = request.body.asJson()?.jsonObject ?: return

        body["contents"]?.let {
            for ((index, message) in it.jsonArray.withIndex()) {
                span.setAttribute("gen_ai.prompt.$index.role", message.jsonObject["role"]?.jsonPrimitive?.content)

                val parts = message.jsonObject["parts"]
                val textMessage = parts?.singleTextMessageInParts()

                if (textMessage != null) {
                    span.setAttribute("gen_ai.prompt.$index.content", textMessage)
                } else {
                    span.setAttribute("gen_ai.prompt.$index.content", parts.toString())
                }
            }
        }

        // url ends with `[model]:[operation]`
        val (model, operation) = request.url.pathSegments.lastOrNull()?.split(":")
            ?.let { it.firstOrNull() to it.lastOrNull() } ?: (null to null)

        model?.let { span.setAttribute(GEN_AI_REQUEST_MODEL, model) }
        operation?.let { span.setAttribute(GEN_AI_OPERATION_NAME, operation) }

        // extract tool calls
        body.jsonObject["tools"]?.let { tools ->
            if (tools is JsonArray) {
                for ((index, tool) in tools.jsonArray.withIndex()) {
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
        body["generationConfig"]?.let { config ->
            config.jsonObject["candidateCount"]?.jsonPrimitive?.intOrNull?.let {
                span.setAttribute(GEN_AI_REQUEST_CHOICE_COUNT, it.toLong())
            }
            config.jsonObject["maxOutputTokens"]?.jsonPrimitive?.intOrNull?.let {
                span.setAttribute(GEN_AI_REQUEST_MAX_TOKENS, it.toLong())
            }
            config.jsonObject["temperature"]?.jsonPrimitive?.doubleOrNull?.let { span.setAttribute(GEN_AI_REQUEST_TEMPERATURE, it) }
            config.jsonObject["topP"]?.jsonPrimitive?.doubleOrNull?.let { span.setAttribute(GEN_AI_REQUEST_TOP_P, it) }
            config.jsonObject["topK"]?.jsonPrimitive?.doubleOrNull?.let { span.setAttribute(GEN_AI_REQUEST_TOP_K, it) }
        }

        span.populateUnmappedAttributes(body, mappedAttributes, PayloadType.REQUEST)
    }

    override fun getResponseBodyAttributes(span: Span, response: Response) {
        // See: https://ai.google.dev/api/generate-content#v1beta.GenerateContentResponse
        val body = response.body.asJson()?.jsonObject ?: return

        body["responseId"]?.let { span.setAttribute(GEN_AI_RESPONSE_ID, it.jsonPrimitive.content) }
        body["modelVersion"]?.let { span.setAttribute(GEN_AI_RESPONSE_MODEL, it.jsonPrimitive.content) }

        body["candidates"]?.let {
            for ((index, candidate) in it.jsonArray.withIndex()) {
                candidate.jsonObject["content"]?.let { content ->
                    span.setAttribute(
                        "gen_ai.completion.$index.role",
                        content.jsonObject["role"]?.jsonPrimitive?.content
                    )
                    // response parts
                    val parts = content.jsonObject["parts"]
                    val textMessage = parts?.singleTextMessageInParts()

                    if (textMessage != null) {
                        span.setAttribute("gen_ai.completion.$index.content", textMessage)
                    } else {
                        span.setAttribute("gen_ai.completion.$index.content", parts.toString())
                    }

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
                span.setAttribute(GEN_AI_USAGE_INPUT_TOKENS, it)
            }
            usage.jsonObject["candidatesTokenCount"]?.jsonPrimitive?.intOrNull?.let {
                span.setAttribute(GEN_AI_USAGE_OUTPUT_TOKENS, it)
            }
            usage.jsonObject["totalTokenCount"]?.jsonPrimitive?.intOrNull?.let {
                span.setAttribute("gen_ai.usage.total_tokens", it.toLong())
            }

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

        span.populateUnmappedAttributes(body, mappedAttributes, PayloadType.RESPONSE)
    }

    // streaming is not supported
    override fun isStreamingRequest(request: Request) = false
    override fun handleStreaming(span: Span, url: Url, events: String) = Unit

    /**
     * Extracts `text` attribute from `parts` array if
     * `parts` contains only a single message with a single
     * `text` attribute.
     *
     * Examples:
     * 1. `text` will be returned:
     * ```json
     * {
     *     "parts": [
     *         {
     *             "text": "Hello! I am a large language model!"
     *         }
     *     ]
     * }
     * ```
     * 2. `null` will be returned (i.e., clients are expected to attach an entire `parts` array into span):
     * ```json
     * {
     *     "parts": [
     *         {
     *             "text": "Hello! I am a large language model.",
     *             "thoughtSignature": "CvcBAR/123"
     *         }
     *     ]
     * }
     * ```
     */
    private fun JsonElement.singleTextMessageInParts(): String? {
        val parts = this
        if (parts !is JsonArray || parts.size != 1) {
            return null
        }
        val item = parts.first().jsonObject
        // only the 'text' attribute is present -> display it on Langfuse with Markdown rendering
        if (item.keys.size == 1 && item.keys.first() == "text") {
            return item["text"]?.jsonPrimitive?.content
        }
        return null
    }

    private fun extractUsageTokenDetails(span: Span, usage: JsonElement, attribute: String) {
        // turn the given attribute into snake-cased format
        val snakeCasedAttribute = attribute.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()

        usage.jsonObject[attribute]?.let { usage ->
            for ((index, detail) in usage.jsonArray.withIndex()) {
                detail.jsonObject["modality"]?.let {
                    span.setAttribute("gen_ai.usage.$snakeCasedAttribute.$index.modality", it.jsonPrimitive.content)
                }
                detail.jsonObject["tokenCount"]?.jsonPrimitive?.intOrNull?.let {
                    span.setAttribute("gen_ai.usage.$snakeCasedAttribute.$index.token_count", it.toLong())
                }
            }
        }
    }

    companion object {
        private val mappedRequestAttributes: List<String> = listOf(
            "contents",
            "tools",
            "generationConfig"
        )

        private val mappedResponseAttributes: List<String> = listOf(
            "responseId",
            "modelVersion",
            "candidates",
            "usageMetadata"
        )

        private val mappedAttributes = mappedRequestAttributes + mappedResponseAttributes
    }
}