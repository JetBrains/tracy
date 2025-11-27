package ai.dev.kit.adapters.openai.handlers

import ai.dev.kit.adapters.LLMTracingAdapter.Companion.PayloadType
import ai.dev.kit.adapters.LLMTracingAdapter.Companion.populateUnmappedAttributes
import ai.dev.kit.adapters.media.MediaContent
import ai.dev.kit.adapters.media.MediaContentExtractor
import ai.dev.kit.adapters.media.MediaContentPart
import ai.dev.kit.adapters.media.Resource
import ai.dev.kit.common.isValidUrl
import ai.dev.kit.http.protocol.Request
import ai.dev.kit.http.protocol.Response
import ai.dev.kit.http.protocol.asJson
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.*
import kotlinx.serialization.json.*

/**
 * Handler for OpenAI Responses API
 */
internal class ResponsesApiHandler(
    private val extractor: MediaContentExtractor
) : OpenAIApiHandler {
    override fun handleRequestAttributes(span: Span, request: Request) {
        val body = request.body.asJson()?.jsonObject ?: return
        OpenAIApiUtils.setCommonRequestAttributes(span, request)

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

        var promptIndex = 0

        body["instructions"]?.jsonPrimitive?.let {
            span.setAttribute("gen_ai.prompt.0.content", it.toString())
            span.setAttribute("gen_ai.prompt.0.role", "system")
            promptIndex++
        }

        body["input"]?.let { inputs ->
            if (inputs is JsonArray) {
                processAttributeTypes(span, inputs, promptIndex, "prompt")
                attachMediaContentAttributes(span, field = "input", inputs)
            } else {
                span.setAttribute("gen_ai.prompt.0.role", "user")
                span.setAttribute("gen_ai.prompt.0.content", inputs.toString())
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

        span.populateUnmappedAttributes(body, mappedAttributes, PayloadType.REQUEST)
    }

    /**
     * @param field must be one of: 'input', 'output' or 'metadata' (see [ai.dev.kit.exporters.http.MediaUploadParams.field])
     */
    private fun attachMediaContentAttributes(span: Span, field: String, inputs: JsonArray) {
        // set attributes with media attachments info into the span
        for (input in inputs) {
            val content = input.jsonObject["content"]
            if (content is JsonArray) {
                val mediaContent = parseMediaContent(content)
                extractor.setUploadableContentAttributes(span, field, mediaContent)
            }
        }
    }

    override fun handleResponseAttributes(span: Span, response: Response) {
        val body = response.body.asJson()?.jsonObject ?: return
        OpenAIApiUtils.setCommonResponseAttributes(span, response)

        body["output"]?.let { outputs ->
            processAttributeTypes(span, outputs.jsonArray, 0, "completion")
        }

        body["usage"]?.let { usage ->
            setUsageAttributes(span, usage.jsonObject)
        }

        span.populateUnmappedAttributes(body, mappedAttributes, PayloadType.RESPONSE)
    }

    override fun handleStreaming(span: Span, events: String): Unit = runCatching {
        for (line in events.lineSequence()) {
            if (!line.startsWith("data:")) continue
            val data = line.removePrefix("data:").trim()

            val obj = runCatching { Json.parseToJsonElement(data).jsonObject }.getOrNull() ?: continue
            if (obj["type"]?.jsonPrimitive?.content == "response.output_text.done") {
                obj["text"]?.jsonPrimitive?.content?.let { finalText ->
                    span.setAttribute("gen_ai.completion.0.content", finalText)
                    span.setAttribute("gen_ai.completion.0.finish_reason", "stop")
                }
            }
        }
    }.getOrElse { exception ->
        span.setStatus(StatusCode.ERROR)
        span.recordException(exception)
    }

    private fun processAttributeTypes(span: Span, events: JsonArray, indexOfFirstAttribute: Int, type: String) {
        var index = indexOfFirstAttribute

        for (output in events.jsonArray) {
            // See: https://platform.openai.com/docs/api-reference/responses/create#responses_create-input
            when (output.jsonObject["type"]?.jsonPrimitive?.content) {
                "function_call", "function_call_output" -> {
                    // "type" attribute is not rendered on Langfuse
                    output.jsonObject["type"]?.jsonPrimitive?.content?.let {
                        span.setAttribute("gen_ai.$type.$index.tool_call_type", it)
                    }
                    output.jsonObject["call_id"]?.jsonPrimitive?.content?.let {
                        span.setAttribute("gen_ai.$type.$index.tool_call_id", it)
                    }
                    output.jsonObject["name"]?.jsonPrimitive?.content?.let {
                        span.setAttribute("gen_ai.$type.$index.tool_name", it)
                    }
                    output.jsonObject["arguments"]?.jsonPrimitive?.content?.let {
                        span.setAttribute("gen_ai.$type.$index.tool_arguments", it)
                    }
                    output.jsonObject["output"]?.jsonPrimitive?.content?.let {
                        span.setAttribute("gen_ai.$type.$index.output", it)
                    }
                }

                "message", null -> {
                    output.jsonObject["role"]?.jsonPrimitive?.content?.let {
                        span.setAttribute("gen_ai.$type.$index.role", it)
                    }

                    val content = output.jsonObject["content"]
                    if (content is JsonArray) {
                        val textContent = content.firstOrNull {
                            it.jsonObject["type"]?.jsonPrimitive?.content == "output_text"
                        }?.jsonObject

                        textContent?.get("text")?.jsonPrimitive?.content?.let {
                            span.setAttribute("gen_ai.$type.$index.content", it)
                        }

                        textContent?.get("annotations")?.let {
                            span.setAttribute("gen_ai.$type.$index.annotations", it.toString())
                        }
                    } else {
                        span.setAttribute("gen_ai.$type.$index.content", content.toString())
                    }

                    output.jsonObject["status"]?.jsonPrimitive?.content?.let {
                        span.setAttribute("gen_ai.$type.$index.finish_reason", it)
                    }
                }

                "reasoning" -> {
                    // "type" attribute is not rendered on Langfuse
                    output.jsonObject["type"]?.jsonPrimitive?.content?.let {
                        span.setAttribute("gen_ai.$type.$index.output_type", it)
                    }
                    output.jsonObject["id"]?.jsonPrimitive?.content?.let {
                        span.setAttribute("gen_ai.$type.$index.id", it)
                    }
                    output.jsonObject["encrypted_content"]?.jsonPrimitive?.content?.let {
                        span.setAttribute("gen_ai.$type.$index.encrypted_content", it)
                    }
                    output.jsonObject["status"]?.jsonPrimitive?.content?.let {
                        span.setAttribute("gen_ai.$type.$index.status", it)
                    }
                    output.jsonObject["summary"]?.jsonArray?.let {
                        span.setAttribute("gen_ai.$type.$index.summary", it.toString())
                    }

                    // content = null breaks rendering on Langfuse
                    output.jsonObject["content"]?.jsonArray?.let {
                        span.setAttribute("gen_ai.$type.$index.output_content", it.toString())
                    }
                }
            }
            index++
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

    /**
     * Extracts media content parts (images, files) from JSON content.
     *
     * See details: [Responses API](https://platform.openai.com/docs/api-reference/responses/create)
     */
    private fun parseMediaContent(content: JsonArray): MediaContent {
        val parts = buildList {
            for (part in content) {
                val type = part.jsonObject["type"]?.jsonPrimitive?.content ?: continue

                val mediaPart = when (type) {
                    "input_image" -> {
                        val url = part.jsonObject["image_url"]?.jsonPrimitive?.content ?: continue
                        if (url.isValidUrl()) {
                            MediaContentPart(Resource.Url(url))
                        } else if (url.startsWith("data:")) {
                            MediaContentPart(Resource.DataUrl(url))
                        } else {
                            null
                        }
                    }

                    "input_file" -> {
                        if ("file_url" in part.jsonObject) {
                            val url = part.jsonObject["file_url"]?.jsonPrimitive?.content ?: continue
                            if (url.isValidUrl()) MediaContentPart(Resource.Url(url)) else null
                        } else if ("file_data" in part.jsonObject) {
                            val dataUrl = part.jsonObject["file_data"]?.jsonPrimitive?.content ?: continue
                            MediaContentPart(Resource.DataUrl(dataUrl))
                        } else {
                            null
                        }
                    }

                    else -> null
                }

                // if the media part is valid, append it to the list
                if (mediaPart != null) {
                    add(mediaPart)
                }
            }
        }

        return MediaContent(parts)
    }

    companion object {
        // https://platform.openai.com/docs/api-reference/responses/create
        private val mappedRequestAttributes: List<String> = listOf(
            "temperature",
            "model",
            "previous_response_id",
            "store",
            "top_p",
            "max_output_tokens",
            "truncation",
            "parallel_tool_calls",
            "stream",
            "response_format",
            "tool_choice",
            "reasoning",
            "text",
            "input",
            "instructions",
            "tools",
        )

        // https://platform.openai.com/docs/api-reference/responses/object
        private val mappedResponseAttributes: List<String> = listOf(
            "output",
            "usage"
        )

        private val mappedAttributes = mappedRequestAttributes + mappedResponseAttributes
    }
}
