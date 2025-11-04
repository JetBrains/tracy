package ai.dev.kit.adapters.openai

import ai.dev.kit.adapters.Url
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_MODEL
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_USAGE_INPUT_TOKENS
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_USAGE_OUTPUT_TOKENS
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.path.Path

/**
 * Extracts request/response bodies of Image Generation API.
 *
 * See [Image Generation API](https://platform.openai.com/docs/api-reference/images/create)
 */
class ImagesGenerationsHandler : OpenAIApiHandler {
    override fun handleRequestAttributes(span: Span, url: Url, body: JsonObject) {
        body["prompt"]?.let { span.setAttribute("gen_ai.prompt.0.content", it.jsonPrimitive.content) }
        body["model"]?.let { span.setAttribute(GEN_AI_REQUEST_MODEL, it.jsonPrimitive.content) }

        val manuallyParsedKeys = listOf("prompt", "model")

        for ((key, value) in body.entries) {
            if (key in manuallyParsedKeys) {
                continue
            }
            span.setAttribute("gen_ai.request.$key", value.asString)
        }
    }

    override fun handleResponseAttributes(span: Span, body: JsonObject) {
        body["usage"]?.jsonObject?.let { setUsageAttributes(span, it) }

        body["data"]?.jsonArray?.let { data ->
            for ((index, value) in data.withIndex()) {
                val image = value.jsonObject
                if (image.hasNonNull("b64_json")) {
                    val bytesEncoded = image["b64_json"]?.jsonPrimitive
                    // TODO: attach for media upload
                }
                else if (image.hasNonNull("url")) {
                    val url = image["url"]?.jsonPrimitive
                    // TODO: attach for media upload
                }
                // TODO: when `b64_json`/`revised_prompt` are null, should they be attached still? (rn, they are)
                 span.setAttribute("gen_ai.completion.$index.content", image.asString)
            }
        }

        val manuallyParsedKeys = listOf("data", "usage")

        for ((key, value) in body.entries) {
            if (key in manuallyParsedKeys) {
                continue
            }
            span.setAttribute("gen_ai.response.$key", value.asString)
        }
    }

    override fun handleStreaming(span: Span, events: String) {
        // TODO: impl
        println("Streaming:")
        for (line in events.lineSequence()) {
            println(line)
        }
    }

    private fun setUsageAttributes(span: Span, usage: JsonObject) {
        usage["input_tokens"]?.jsonPrimitive?.intOrNull?.let {
            span.setAttribute(GEN_AI_USAGE_INPUT_TOKENS, it)
        }
        usage["output_tokens"]?.jsonPrimitive?.intOrNull?.let {
            span.setAttribute(GEN_AI_USAGE_OUTPUT_TOKENS, it)
        }

        usage["input_tokens_details"]?.jsonObject?.let {
            span.setAttribute("gen_ai.usage.input_tokens_details", it.asString)
        }
        usage["total_tokens"]?.jsonPrimitive?.intOrNull?.let {
            span.setAttribute(AttributeKey.longKey("gen_ai.usage.total_tokens"), it)
        }
    }

    /**
     * Checks that this JSON object contains the key and
     * its value is not an explicit `null`, i.e., [JsonNull].
     */
    private fun JsonObject.hasNonNull(key: String): Boolean {
        val obj = this
        return (obj[key] != null) && (obj[key] !is JsonNull)
    }
}