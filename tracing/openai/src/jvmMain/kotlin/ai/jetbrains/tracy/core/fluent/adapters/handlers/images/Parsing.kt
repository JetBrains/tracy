package ai.jetbrains.tracy.core.fluent.adapters.handlers.images

import ai.jetbrains.tracy.core.adapters.media.MediaContent
import ai.jetbrains.tracy.core.adapters.media.MediaContentExtractor
import ai.jetbrains.tracy.core.adapters.media.MediaContentPart
import ai.jetbrains.tracy.core.adapters.media.Resource
import ai.jetbrains.tracy.core.fluent.adapters.handlers.asString
import ai.jetbrains.tracy.core.http.protocol.Response
import ai.jetbrains.tracy.core.http.protocol.asJson
import ai.jetbrains.tracy.core.tracing.policy.ContentKind
import ai.jetbrains.tracy.core.tracing.policy.contentTracingAllowed
import ai.jetbrains.tracy.core.tracing.policy.orRedactedOutput
import io.ktor.http.ContentType
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_USAGE_INPUT_TOKENS
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_USAGE_OUTPUT_TOKENS
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.collections.component1
import kotlin.collections.component2
import mu.KotlinLogging.logger


// See: https://platform.openai.com/docs/api-reference/images/create#images_create-output_format
private const val defaultImageFormat = "png"

private val logger = logger {}

internal fun handleImageGenerationResponseAttributes(
    span: Span,
    response: Response,
    extractor: MediaContentExtractor,
) {
    val body = response.body.asJson()?.jsonObject ?: return

    body["data"]?.jsonArray?.let { data ->
        // collect AI response content
        for ((index, image) in data.withIndex()) {
            span.setAttribute("gen_ai.completion.$index.content", image.asString.orRedactedOutput())
        }
        // install media content for further upload
        val format = body["output_format"]?.jsonPrimitive?.content ?: defaultImageFormat
        val contentType = "image/$format"

        if (contentTracingAllowed(ContentKind.OUTPUT)) {
            val mediaContent = parseMediaContent(data, contentType)
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

internal fun handleStreamedImage(
    span: Span,
    data: JsonObject,
    extractor: MediaContentExtractor,
    completedType: String,
    partialImageType: String,
) {
    val type = data["type"]?.jsonPrimitive?.content ?: return

    when (type) {
        completedType -> {
            val base64 = data["b64_json"]?.jsonPrimitive?.content ?: return
            // install image data as JSON object: `{ "b64_json": "data" }`
            val content = Json.parseToJsonElement("""
                {"b64_json": "$base64"}
            """.trimIndent())
            span.setAttribute("gen_ai.completion.0.content", content.asString.orRedactedOutput())

            data["usage"]?.jsonObject?.let { setUsageAttributes(span, it) }

            // insert other attributes
            val manuallyParsedKeys = listOf("b64_json", "usage")
            for ((key, value) in data.entries) {
                if (key !in manuallyParsedKeys) {
                    span.setAttribute("gen_ai.response.$key", value.asString)
                }
            }
        }
        partialImageType -> {
            val partialImageIndex = data["partial_image_index"]?.jsonPrimitive?.intOrNull ?: return
            // insert attributes in `gen_ai.completion.partial_image.[index].*`
            for ((key, value) in data.entries) {
                span.setAttribute(
                    "gen_ai.completion.partial_image.$partialImageIndex.$key",
                    value.asString.orRedactedOutput()
                )
            }
        }
    }

    // install media content for further upload
    if (contentTracingAllowed(ContentKind.OUTPUT)) {
        val base64 = data["b64_json"]?.jsonPrimitive?.content ?: return
        val format = data["output_format"]?.jsonPrimitive?.content ?: defaultImageFormat
        val contentType = try {
            ContentType.parse("image/$format")
        } catch (err: Exception) {
            logger.trace("Failed to parse content type: 'image/$format'. Skipping this content part", err)
            null
        } ?: return

        val content = MediaContent(parts = listOf(
            MediaContentPart(Resource.Base64(base64, contentType))
        ))
        extractor.setUploadableContentAttributes(span, field = "output", content)
    }
}

private fun parseMediaContent(data: JsonArray, contentType: String): MediaContent {
    val parts = buildList {
        for (part in data) {
            val image = part.jsonObject
            val contentPart = if (image.hasNonNull("b64_json")) {
                val base64 = image["b64_json"]?.jsonPrimitive?.content ?: continue

                val contentType = try {
                    ContentType.parse(contentType)
                } catch (err: Exception) {
                    logger.trace("Failed to parse content type: '$contentType'. Skipping this data part", err)
                    null
                } ?: continue

                MediaContentPart(Resource.Base64(base64, contentType))
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