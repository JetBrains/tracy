package ai.jetbrains.tracy.gemini.adapters.handlers

import ai.jetbrains.tracy.core.adapters.handlers.EndpointApiHandler
import ai.jetbrains.tracy.core.adapters.media.MediaContent
import ai.jetbrains.tracy.core.adapters.media.MediaContentExtractor
import ai.jetbrains.tracy.core.adapters.media.MediaContentPart
import ai.jetbrains.tracy.core.adapters.media.Resource
import ai.jetbrains.tracy.core.adapters.media.parseSafe
import ai.jetbrains.tracy.core.http.protocol.Request
import ai.jetbrains.tracy.core.http.protocol.Response
import ai.jetbrains.tracy.core.http.protocol.asJson
import io.ktor.http.ContentType
import io.opentelemetry.api.trace.Span
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging

/**
 * Parses Imagen API requests and responses
 *
 * See: [Imagen API Docs](https://ai.google.dev/gemini-api/docs/imagen)
 */
class GeminiImagenHandler(
    private val extractor: MediaContentExtractor
) : EndpointApiHandler {
    override fun handleRequestAttributes(span: Span, request: Request) {
        val body = request.body.asJson()?.jsonObject ?: return

        val instances = body["instances"]?.jsonArray ?: return
        for ((index, instance) in instances.withIndex()) {
            span.setAttribute("gen_ai.prompt.$index.content", instance.jsonObject["prompt"]?.jsonPrimitive?.content)
        }

        // build resources from the input images
        val images: List<Resource> = buildList {
            for (instance in instances) {
                val image = instance.jsonObject["image"]?.jsonObject ?: continue
                val resource = parseImagenImage(image) ?: continue
                add(resource)
            }
        }
        // build resources and other image attributes from the input reference images
        val referenceImages: List<Pair<Resource, JsonObject>> = buildList {
            for (instance in instances) {
                val referenceImages = instance.jsonObject["referenceImages"]?.jsonArray ?: continue
                for (image in referenceImages) {
                    // create resource from image data
                    val imageRef = image.jsonObject["referenceImage"]?.jsonObject ?: continue
                    val resource = parseImagenImage(imageRef) ?: continue
                    // save other attributes of this image (excluding "referenceImage")
                    val attributes = Json.encodeToJsonElement(
                        image.jsonObject.filterKeys { it != "referenceImage" }
                    ).jsonObject
                    add(resource to attributes)
                }
            }
        }
        // set reference image attributes into span
        for ((index, attributes) in referenceImages.map { it.second }.withIndex()) {
            span.setAttribute("tracy.request.referenceImage.$index.attributes", attributes.toString())
        }
        // save media content for upload
        val mediaContent = run {
            val resources = images + referenceImages.map { it.first }
            MediaContent(parts = resources.map { MediaContentPart(it) })
        }
        extractor.setUploadableContentAttributes(span, field = "input", mediaContent)

        body["parameters"]?.let { span.setAttribute("tracy.request.imagen.parameters", it.toString()) }
    }

    override fun handleResponseAttributes(span: Span, response: Response) {
        val body = response.body.asJson()?.jsonObject ?: return

        val predictions = body["predictions"]?.jsonArray ?: return
        for ((index, prediction) in predictions.withIndex()) {
            span.setAttribute(
                "gen_ai.completion.$index.content",
                prediction.jsonObject["prompt"]?.jsonPrimitive?.content
            )
        }
        val resources = parseImagenImages(predictions)

        // setting generated images for upload
        val mediaContent = MediaContent(resources.map { MediaContentPart(it) })
        extractor.setUploadableContentAttributes(span, field = "output", mediaContent)
    }

    override fun handleStreaming(span: Span, events: String) = Unit

    /**
     * Expects an array of schemas:
     * ```json
     * {
     *    "mimeType": "string",
     *    "bytesBase64Encoded": "string"
     * }
     * ```
     */
    private fun parseImagenImages(images: JsonArray): List<Resource> = buildList {
        for (image in images) {
            val resource = parseImagenImage(image.jsonObject) ?: continue
            add(resource)
        }
    }

    private fun parseImagenImage(image: JsonObject): Resource? {
        val mimeType = image["mimeType"]?.jsonPrimitive?.content ?: return null
        val base64 = image["bytesBase64Encoded"]?.jsonPrimitive?.content ?: return null
        val contentType = ContentType.parseSafe(mimeType)

        if (contentType == null) {
            logger.warn("Cannot convert the mime type '$mimeType' to content type")
            return null
        }

        return Resource.Base64(base64, contentType)
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}