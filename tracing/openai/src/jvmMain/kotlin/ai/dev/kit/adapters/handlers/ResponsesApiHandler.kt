package ai.dev.kit.adapters.handlers

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

        body["instructions"]?.jsonPrimitive?.let {
            span.setAttribute("gen_ai.prompt.0.content", it.toString())
            span.setAttribute("gen_ai.prompt.0.role", "system")
        }
        body["input"]?.let { inputs ->
            if (inputs is JsonArray) {
                parseRequestInputAttributes(span, inputs)
                attachMediaContentAttributes(span, inputs)
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

    private fun attachMediaContentAttributes(span: Span, inputs: JsonArray) {
        // set attributes with media attachments info into the span
        for (input in inputs) {
            val content = input.jsonObject["content"]
            if (content is JsonArray) {
                val mediaContent = parseMediaContent(content)
                extractor.setUploadableContentAttributes(span, field = "input", mediaContent)
            }
        }
    }

    /**
     * Parses attributes from the Response Object of the Responses API.
     *
     * See [Response Object, Responses API](https://platform.openai.com/docs/api-reference/responses/object)
     */
    override fun handleResponseAttributes(span: Span, response: Response) {
        val body = response.body.asJson()?.jsonObject ?: return
        OpenAIApiUtils.setCommonResponseAttributes(span, response)

        // we manually map `output` and `usage` attributes;
        // the rest of attributes get mapped by `populateUnmappedAttributes` below.
        body["output"]?.let { outputs ->
            for ((index, output) in outputs.jsonArray.withIndex()) {
                when (val type = output.jsonObject["type"]?.jsonPrimitive?.content) {
                    "message", null -> {
                        // See schema: https://platform.openai.com/docs/api-reference/responses/object#responses-object-output-output_message
                        output.jsonObject["role"]?.jsonPrimitive?.content?.let {
                            span.setAttribute("gen_ai.completion.$index.role", it)
                        }
                        output.jsonObject["id"]?.jsonPrimitive?.content?.let {
                            span.setAttribute("gen_ai.completion.$index.id", it)
                        }
                        output.jsonObject["status"]?.jsonPrimitive?.content?.let {
                            span.setAttribute("gen_ai.completion.$index.finish_reason", it)
                        }

                        val content = output.jsonObject["content"]
                        // See schema: https://platform.openai.com/docs/api-reference/responses/object#responses-object-output-output_message-content
                        if (content is JsonArray) {
                            // if there is a single message that has a type of `output_text`, then install it as completion content;
                            // otherwise, set the entire array instead.
                            if (content.size == 1 && content.first().jsonObject["type"]?.jsonPrimitive?.contentOrNull == "output_text") {
                                val message = content
                                    .first { it.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "output_text" }
                                    .jsonObject

                                message["text"]?.jsonPrimitive?.content?.let {
                                    span.setAttribute(
                                        "gen_ai.completion.$index.content",
                                        it
                                    )
                                }
                                message["annotations"]?.let {
                                    span.setAttribute(
                                        "gen_ai.completion.$index.annotations",
                                        it.toString()
                                    )
                                }
                            } else {
                                // set the entire array as completion content
                                span.setAttribute("gen_ai.completion.$index.content", content.toString())
                            }
                        } else if (content != null) {
                            span.setAttribute("gen_ai.completion.$index.content", content.toString())
                        }
                    }

                    else -> {
                        // any other types, including 'function_call' and 'reasoning'
                        // See output types: https://platform.openai.com/docs/api-reference/responses/object#responses-object-output
                        for ((k, v) in output.jsonObject.entries) {
                            val key = when {
                                // prefix `function_call` with "tool_"
                                type == "function_call" && k == "type" -> "tool_call_type"
                                type == "function_call" -> "tool_$k"
                                // special treatment for content of `reasoning`
                                type == "reasoning" && k == "content" -> "output_content"
                                // special treatment of `type` field
                                k == "type" -> "output_type"
                                else -> k
                            }
                            val value = when {
                                v is JsonPrimitive -> v.content
                                else -> v.toString()
                            }
                            span.setAttribute("gen_ai.completion.$index.$key", value)
                        }
                    }
                }
            }
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

    /**
     * Parses input field of the request when it is of an array type.
     *
     * See the [schema](https://platform.openai.com/docs/api-reference/responses/create#responses_create-input)
     */
    private fun parseRequestInputAttributes(span: Span, inputs: JsonArray) {
        for ((index, input) in inputs.withIndex()) {
            // See: https://platform.openai.com/docs/api-reference/responses/create#responses_create-input
            when (val type = input.jsonObject["type"]?.jsonPrimitive?.content) {
                "message" -> {
                    // this message can be either:
                    //   1. Input message: https://platform.openai.com/docs/api-reference/responses/create#responses_create-input-input_item_list-item-input_message
                    //   2. Output message: https://platform.openai.com/docs/api-reference/responses/create#responses_create-input-input_item_list-item-output_message
                    // the difference is in the `role` and `content` fields

                    // install primitive keys common for both input and output messages
                    val fields = listOf("id", "role", "status", "type")
                    for (field in fields) {
                        input.jsonObject[field]?.jsonPrimitive?.content?.let { value ->
                            val key = when (field) {
                                "type" -> "input_type"
                                else -> field
                            }
                            span.setAttribute("gen_ai.prompt.$index.$key", value)
                        }
                    }

                    val content = input.jsonObject["content"]
                    if (content is JsonArray) {
                        // if there is a single message that has a type of `input_text`, then install it as prompt content;
                        // otherwise, set the entire array instead.
                        if (content.size == 1 && content.first().jsonObject["type"]?.jsonPrimitive?.content == "input_text") {
                            val message = content
                                .first { it.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "input_text" }
                                .jsonObject

                            message["text"]?.jsonPrimitive?.content?.let {
                                span.setAttribute(
                                    "gen_ai.prompt.$index.content",
                                    it
                                )
                            }
                            message["type"]?.jsonPrimitive?.content?.let {
                                span.setAttribute(
                                    "gen_ai.prompt.$index.content_type",
                                    it
                                )
                            }
                        } else {
                            // set the entire array as prompt content
                            span.setAttribute("gen_ai.prompt.$index.content", content.toString())
                        }

                    } else if (content != null) {
                        span.setAttribute("gen_ai.prompt.$index.content", content.toString())
                    }
                }

                else -> {
                    // any other types, including 'function_call_output' and 'reasoning'
                    // See input types: https://platform.openai.com/docs/api-reference/responses/create#responses_create-input-input_item_list-item
                    for ((k, v) in input.jsonObject.entries) {
                        val functionCallTypes = listOf("function_call", "function_call_output")
                        val key = when {
                            // prefix `function_call`/`function_call_output` with "tool_"
                            (type in functionCallTypes) && k == "type" -> "tool_call_type"
                            (type in functionCallTypes) && k == "output" -> "output"
                            type in functionCallTypes -> "tool_$k"
                            // special treatment for content of `reasoning`
                            type == "reasoning" && k == "content" -> "output_content"
                            // special treatment of `type` field
                            k == "type" -> "output_type"
                            else -> k
                        }
                        val value = when {
                            v is JsonPrimitive -> v.content
                            else -> v.toString()
                        }
                        span.setAttribute("gen_ai.prompt.$index.$key", value)
                    }
                }
            }
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
            // parsed by `OpenAIApiUtils.setCommonResponseAttributes`
            "id",
            "object",
            "model",

            "output",
            "usage",
        )

        private val mappedAttributes = mappedRequestAttributes + mappedResponseAttributes
    }
}
