/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package ai.jetbrains.tracy.openai.adapters.handlers.images

import ai.jetbrains.tracy.core.adapters.handlers.EndpointApiHandler
import ai.jetbrains.tracy.core.adapters.media.MediaContent
import ai.jetbrains.tracy.core.adapters.media.MediaContentExtractor
import ai.jetbrains.tracy.core.adapters.media.MediaContentPart
import ai.jetbrains.tracy.core.adapters.media.Resource
import ai.jetbrains.tracy.core.http.protocol.Request
import ai.jetbrains.tracy.core.http.protocol.Response
import ai.jetbrains.tracy.core.http.protocol.asFormData
import ai.jetbrains.tracy.core.policy.ContentKind
import ai.jetbrains.tracy.core.policy.contentTracingAllowed
import ai.jetbrains.tracy.core.policy.orRedactedInput
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
    private val extractor: MediaContentExtractor
) : EndpointApiHandler {
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

            // decode content based on the expected content type
            val content = when(contentType.type) {
                "image" -> Base64.getEncoder().encodeToString(part.content)
                "text" -> part.content.toString(
                    contentType.charset() ?: Charsets.UTF_8
                )
                else -> null
            }

            if (content == null) {
                logger.warn { "Form data part '${part.name}' with content type '$contentType' has no content" }
                continue
            }

            when (part.name) {
                "prompt" -> {
                    span.setAttribute("gen_ai.prompt.0.content", content.orRedactedInput())
                }

                "model" -> {
                    span.setAttribute(GenAiIncubatingAttributes.GEN_AI_REQUEST_MODEL, content)
                }
                // mask is a single image that should be uploaded as well
                "mask" -> if (contentTracingAllowed(ContentKind.INPUT)) {
                    // trace mask only when input content tracing is allowed.
                    // base64-encoded mask content
                    span.setAttribute("gen_ai.request.mask.content", content)
                    span.setAttribute("gen_ai.request.mask.contentType", contentType.asString())
                    if (part.filename != null) {
                        span.setAttribute("gen_ai.request.mask.filename", part.filename)
                    }
                    // save mask for further upload
                    mediaContentParts.add(
                        MediaContentPart(resource = Resource.Base64(content, contentType.asString()))
                    )
                }
                // either a single image or an array of images
                "image", "image[]" -> if (contentTracingAllowed(ContentKind.INPUT)) {
                    // trace images only when input content tracing is allowed.
                    // base64-encoded image content
                    span.setAttribute("gen_ai.request.image.$imagesCount.content", content)
                    span.setAttribute("gen_ai.request.image.$imagesCount.contentType", contentType.asString())
                    if (part.filename != null) {
                        span.setAttribute("gen_ai.request.image.$imagesCount.filename", part.filename)
                    }
                    // save image for further upload
                    mediaContentParts.add(
                        MediaContentPart(resource = Resource.Base64(content, contentType.asString()))
                    )
                    ++imagesCount
                }

                null -> logger.warn { "Form data part with missing name ignored. Content type: '$contentType'" }
                else -> {
                    // since we don't know how sensitive other fields may be,
                    // we disguise their content if input tracing is disallowed.
                    span.setAttribute("gen_ai.request.${part.name}", content.orRedactedInput())
                }
            }
        }

        if (contentTracingAllowed(ContentKind.INPUT)) {
            extractor.setUploadableContentAttributes(
                span,
                field = "input",
                content = MediaContent(mediaContentParts),
            )
        }
    }

    override fun handleResponseAttributes(span: Span, response: Response) {
        handleImageGenerationResponseAttributes(span, response, extractor)
    }

    override fun handleStreaming(span: Span, events: String) {
        for (line in events.lineSequence()) {
            if (!line.startsWith("data:")) {
                continue
            }
            val data = try {
                Json.parseToJsonElement(line.removePrefix("data:").trim()).jsonObject
            } catch (err: Exception) {
                logger.trace("Failed to parse streaming data: '$line'", err)
                null
            } ?: continue

            handleStreamedImage(
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