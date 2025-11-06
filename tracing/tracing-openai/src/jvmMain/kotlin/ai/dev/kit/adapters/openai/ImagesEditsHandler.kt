package ai.dev.kit.adapters.openai

import ai.dev.kit.adapters.media.MediaContentExtractor
import ai.dev.kit.adapters.media.MediaContentPart
import ai.dev.kit.http.protocol.Request
import ai.dev.kit.http.protocol.Response
import ai.dev.kit.http.protocol.asFormData
import io.ktor.http.*
import io.opentelemetry.api.trace.Span
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

        for ((index, part) in body.parts.withIndex()) {
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
                logger.warn { "Missing content type of form data part at index $index" }
                continue
            }

            val content = when (contentType.withoutParameters()) {
                ContentType.Application.OctetStream -> {
                    Base64.getEncoder().encodeToString(part.content)
                }
                ContentType.Text.Plain -> {
                    val charset = contentType.charset() ?: Charsets.UTF_8
                    String(part.content, charset)
                }
                else -> {
                    logger.warn { "Skipping form data part at index $index with content type '$contentType'" }
                    continue
                }
            }

            if (part.name == "prompt") {
                // special attribute for prompt
                span.setAttribute("gen_ai.prompt.$index.content", content)
            } else if (part.name != null) {
                span.setAttribute("gen_ai.prompt.$index.${part.name}", content)
            }
            if (part.filename != null) {
                span.setAttribute("gen_ai.prompt.$index.filename", part.filename)
            }

            // TODO: how to define content type?
            /*mediaContentParts.add(MediaContentPart(
                Resource.Base64(content),
                contentType,
            ))*/
        }
    }

    override fun handleResponseAttributes(span: Span, response: Response) {
        //TODO("Not yet implemented")
    }

    override fun handleStreaming(span: Span, events: String) {
        //TODO("Not yet implemented")
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}