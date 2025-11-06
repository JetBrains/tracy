package ai.dev.kit.adapters.openai.handlers.images

import ai.dev.kit.adapters.media.MediaContent
import ai.dev.kit.adapters.media.MediaContentExtractor
import ai.dev.kit.adapters.media.MediaContentPart
import ai.dev.kit.adapters.media.Resource
import ai.dev.kit.adapters.openai.handlers.asString
import ai.dev.kit.http.protocol.Response
import ai.dev.kit.http.protocol.asJson
import io.ktor.http.ContentType
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_USAGE_INPUT_TOKENS
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_USAGE_OUTPUT_TOKENS
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.collections.component1
import kotlin.collections.component2


internal fun handleImageGenerationResponseAttributes(
    span: Span,
    response: Response,
    extractor: MediaContentExtractor,
) {
    val body = response.body.asJson()?.jsonObject ?: return
    // See: https://platform.openai.com/docs/api-reference/images/create#images_create-output_format
    val defaultImageFormat = "png"

    body["data"]?.jsonArray?.let { data ->
        // collect AI response content
        for ((index, image) in data.withIndex()) {
            span.setAttribute("gen_ai.completion.$index.content", image.asString)
        }
        // install media content for further upload
        val format = body["output_format"]?.jsonPrimitive?.content ?: defaultImageFormat
        val contentType = "image/$format"

        val mediaContent = parseMediaContent(data, contentType)
        extractor.setUploadableContentAttributes(span, field = "output", mediaContent)
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

private fun parseMediaContent(data: JsonArray, contentType: String): MediaContent {
    val parts = buildList {
        for (part in data) {
            val image = part.jsonObject
            val contentPart = if (image.hasNonNull("b64_json")) {
                val base64 = image["b64_json"]?.jsonPrimitive?.content ?: continue
                MediaContentPart(Resource.Base64(base64), ContentType.parse(contentType))
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