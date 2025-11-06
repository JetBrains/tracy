package ai.dev.kit.adapters.openai.handlers.images

import ai.dev.kit.adapters.media.MediaContent
import ai.dev.kit.adapters.media.MediaContentExtractor
import ai.dev.kit.adapters.media.MediaContentPart
import ai.dev.kit.adapters.media.Resource
import ai.dev.kit.adapters.openai.handlers.OpenAIApiHandler
import ai.dev.kit.http.protocol.Request
import ai.dev.kit.http.protocol.Response
import ai.dev.kit.http.protocol.asFormData
import io.ktor.http.*
import io.opentelemetry.api.trace.Span
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes
import mu.KotlinLogging
import java.util.*

/**
 * Extracts request/response bodies of Image Edit API.
 *
 * See [Image Edit API](https://platform.openai.com/docs/api-reference/images/createEdit)
 */
internal class ImagesEditsHandler(
    private val extractor: MediaContentExtractor) : OpenAIApiHandler {
    override fun handleRequestAttributes(span: Span, request: Request) {
        val body = request.body.asFormData() ?: return

        val mediaContentParts = mutableListOf<MediaContentPart>()
        var imagesCount = 0

        for (part in body.parts) {
            val s = if (part.content.size > 100) {
                part.content.decodeToString().substring(0, 100)
            } else {
                part.content.decodeToString()
            }
            println("content: '$s'")
            println("contentType: ${part.contentType}")
            println("name: ${part.name}")
            println("filename: ${part.filename}")
            println()

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
                // either a single image or an array of images
                "image", "image[]" -> {
                    // base64-encoded image content
                    span.setAttribute("gen_ai.request.image.$imagesCount.content", content)
                    span.setAttribute("gen_ai.request.image.$imagesCount.contentType", contentType.toString())
                    if (part.filename != null) {
                        span.setAttribute("gen_ai.request.image.$imagesCount.filename", part.filename)
                    }
                    // save image for further upload
                    mediaContentParts.add(MediaContentPart(
                        resource = Resource.Base64(content),
                        contentType,
                    ))
                    ++imagesCount
                }
                null -> { /* no-op */ }
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
        //TODO: implement
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}