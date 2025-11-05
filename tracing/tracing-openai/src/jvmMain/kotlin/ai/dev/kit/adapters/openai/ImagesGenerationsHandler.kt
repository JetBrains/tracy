package ai.dev.kit.adapters.openai

import ai.dev.kit.adapters.media.MediaContent
import ai.dev.kit.adapters.media.MediaContentExtractor
import ai.dev.kit.adapters.media.MediaContentPart
import ai.dev.kit.adapters.media.Resource
import ai.dev.kit.http.protocol.Request
import ai.dev.kit.http.protocol.Response
import ai.dev.kit.http.protocol.asJson
import io.ktor.http.*
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.*
import kotlinx.serialization.json.*

/**
 * Extracts request/response bodies of Image Generation API.
 *
 * See [Image Generation API](https://platform.openai.com/docs/api-reference/images/create)
 */
internal class ImagesGenerationsHandler(
    private val extractor: MediaContentExtractor) : OpenAIApiHandler {
    override fun handleRequestAttributes(span: Span, request: Request) {
        val body = request.body.asJson()?.jsonObject ?: return

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

    override fun handleResponseAttributes(span: Span, response: Response) {
        val body = response.body.asJson()?.jsonObject ?: return

        body["data"]?.jsonArray?.let { data ->
            // collect AI response content
            for ((index, image) in data.withIndex()) {
                span.setAttribute("gen_ai.completion.$index.content", image.asString)
            }
            // install media content for further upload
            val imageFormat = body["output_format"]?.jsonPrimitive?.content
            if (imageFormat != null) {
                val mediaContent = parseMediaContent(data, imageFormat)
                extractor.setUploadableContentAttributes(span, field = "output", mediaContent)
            }
        }

        body["usage"]?.jsonObject?.let { setUsageAttributes(span, it) }

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

    private fun parseMediaContent(data: JsonArray, format: String): MediaContent {
        val parts = buildList {
            for (part in data) {
                val image = part.jsonObject
                val contentPart = if (image.hasNonNull("b64_json")) {
                    val base64 = image["b64_json"]?.jsonPrimitive?.content ?: continue
                    MediaContentPart(Resource.Base64(base64), ContentType.parse("image/$format"))
                }
                else if (image.hasNonNull("url")) {
                    val url = image["url"]?.jsonPrimitive?.content ?: continue
                    MediaContentPart(Resource.Url(url))
                } else {
                    null
                }

                if (contentPart != null) {
                    add(contentPart)
                }
            }
        }

        return MediaContent(parts)
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