/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.openai.adapters.handlers.images

import org.jetbrains.ai.tracy.core.adapters.handlers.EndpointApiHandler
import org.jetbrains.ai.tracy.core.adapters.media.MediaContent
import org.jetbrains.ai.tracy.core.adapters.media.MediaContentExtractor
import org.jetbrains.ai.tracy.core.adapters.media.MediaContentPart
import org.jetbrains.ai.tracy.core.adapters.media.Resource
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpRequest
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpResponse
import org.jetbrains.ai.tracy.core.http.protocol.asFormData
import org.jetbrains.ai.tracy.core.policy.ContentKind
import org.jetbrains.ai.tracy.core.policy.contentTracingAllowed
import org.jetbrains.ai.tracy.core.policy.orRedactedInput
import io.opentelemetry.api.trace.Span
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import mu.KotlinLogging
import org.jetbrains.ai.tracy.core.adapters.handlers.streamingFailure
import org.jetbrains.ai.tracy.core.http.parsers.SseEvent
import java.util.*

/**
 * Extracts request/response bodies of Image Edit API.
 *
 * See [Image Edit API](https://platform.openai.com/docs/api-reference/images/createEdit)
 */
internal class ImagesCreateEditOpenAIApiEndpointHandler(
    private val extractor: MediaContentExtractor
) : EndpointApiHandler {
    override fun handleRequestAttributes(span: Span, request: TracyHttpRequest) {
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
                    contentType.charset() ?: Charsets.US_ASCII
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

    override fun handleResponseAttributes(span: Span, response: TracyHttpResponse) {
        handleImageGenerationResponseAttributes(span, response, extractor)
    }

    override fun handleStreamingEvent(
        span: Span,
        event: SseEvent,
        index: Long,
    ): Result<Boolean> {
        val data = runCatching {
            Json.parseToJsonElement(event.data).jsonObject
        }.getOrNull() ?: return streamingFailure("Cannot parse event data as JSON")

        handleStreamedImage(
            span, data, extractor,
            completedType = "image_edit.completed",
            partialImageType = "image_edit.partial_image",
        )

        return Result.success(true)
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}