package ai.jetbrains.tracy.tracing.adapters.handlers.images

import ai.dev.kit.adapters.handlers.EndpointApiHandler
import ai.dev.kit.adapters.media.MediaContent
import ai.dev.kit.adapters.media.MediaContentExtractor
import ai.dev.kit.adapters.media.MediaContentPart
import ai.dev.kit.adapters.media.Resource
import ai.dev.kit.http.protocol.Request
import ai.dev.kit.http.protocol.Response
import ai.dev.kit.http.protocol.asFormData
import io.ktor.http.*
import io.opentelemetry.api.trace.Span
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import mu.KotlinLogging
import java.util.*

/**
 * Extracts request/response bodies of Image Edit API.
 *
 * See [Image Edit API](https://platform.openai.com/docs/api-reference/images/createEdit)
 */
internal class ImagesCreateEditOpenAIApiEndpointHandler(
    private val extractor: MediaContentExtractor) : EndpointApiHandler {
    override fun handleRequestAttributes(span: Span, request: Request) {
        val body = request.body.asFormData() ?: return

        val mediaContentParts = mutableListOf<MediaContentPart>()
        var imagesCount = 0

        for (part in body.parts) {
            val contentType = part.contentType
            if (contentType == null) {
                logger.warn { "Missing content type of form data part '${part.name}'" }
                continue
            }

            val content = contentType.withoutParameters().let {
                when {
                    it.match(ContentType.Image.Any) ->
                        Base64.getEncoder().encodeToString(part.content)
                    it.match(ContentType.Text.Any) ->
                        part.content.toString(contentType.charset() ?: Charsets.UTF_8)
                    else -> null
                }
            }

            if (content == null) {
                logger.warn { "Form data part '${part.name}' with content type '$contentType' has no content" }
                continue
            }

            when(part.name) {
                "prompt" -> {
                    span.setAttribute("gen_ai.prompt.0.content", content)
                }
                "model" -> {
                    span.setAttribute(GenAiIncubatingAttributes.GEN_AI_REQUEST_MODEL, content)
                }
                // mask is a single image that should be uploaded as well
                "mask" -> {
                    // base64-encoded mask content
                    span.setAttribute("gen_ai.request.mask.content", content)
                    span.setAttribute("gen_ai.request.mask.contentType", contentType.toString())
                    if (part.filename != null) {
                        span.setAttribute("gen_ai.request.mask.filename", part.filename)
                    }
                    // save mask for further upload
                    mediaContentParts.add(
                        MediaContentPart(resource = Resource.Base64(content, contentType))
                    )
                }
                // either a single image or an array of images
                "image", "image[]" -> {
                    // base64-encoded image content
                    span.setAttribute("gen_ai.request.image.$imagesCount.content", content)
                    span.setAttribute("gen_ai.request.image.$imagesCount.contentType", contentType.toString())
                    if (part.filename != null) {
                        span.setAttribute("gen_ai.request.image.$imagesCount.filename", part.filename)
                    }
                    // save image for further upload
                    mediaContentParts.add(
                        MediaContentPart(resource = Resource.Base64(content, contentType))
                    )
                    ++imagesCount
                }
                null -> logger.warn { "Form data part with missing name ignored. Content type: '$contentType'" }
                else -> span.setAttribute("gen_ai.request.${part.name}", content)
            }
        }

        extractor.setUploadableContentAttributes(
            span,
            field = "input",
            content = MediaContent(mediaContentParts),
        )
    }

    override fun handleResponseAttributes(span: Span, response: Response) {
        handleImageGenerationResponseAttributes(span, response, extractor)
    }

    override fun handleStreaming(span: Span, events: String) {
        for (line in events.lineSequence()) {
            if (!line.startsWith("data:")) {
                continue
            }
            val data = Json.parseToJsonElement(line.removePrefix("data:").trim()).jsonObject

            handleStreamingImage(
                span, data, extractor,
                completedType = "image_edit.completed",
                partialImageType = "image_edit.partial_image",
            )
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}