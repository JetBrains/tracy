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
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_OPERATION_NAME
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_OUTPUT_TYPE
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_MODEL
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import mu.KotlinLogging
import java.util.*

/**
 * Extracts request/response bodies of Image Variation API.
 *
 * See [Image Variation API](https://platform.openai.com/docs/api-reference/images/createVariation)
 */
internal class ImagesCreateVariationOpenAIApiEndpointHandler(
    private val extractor: MediaContentExtractor
) : EndpointApiHandler {
    override fun handleRequestAttributes(span: Span, request: TracyHttpRequest) {
        span.setAttribute(GEN_AI_OPERATION_NAME, "generate_content")
        span.setAttribute(GEN_AI_OUTPUT_TYPE, "image")

        val body = request.body.asFormData() ?: return

        val mediaContentParts = mutableListOf<MediaContentPart>()
        var imagesCount = 0

        for (part in body.parts) {
            val contentType = part.contentType
            if (contentType == null) {
                logger.warn { "Missing content type of form data part '${part.name}'" }
                continue
            }

            val content = when (contentType.type) {
                "image" -> Base64.getEncoder().encodeToString(part.content)
                "text" -> part.content.toString(contentType.charset() ?: Charsets.US_ASCII)
                else -> null
            }

            if (content == null) {
                logger.warn { "Form data part '${part.name}' with content type '$contentType' has no content" }
                continue
            }

            when (part.name) {
                "model" -> span.setAttribute(GEN_AI_REQUEST_MODEL, content)
                "n" -> content.toLongOrNull()?.let { span.setAttribute("tracy.request.n", it) }
                    ?: span.setAttribute("tracy.request.n", content.orRedactedInput())
                "size" -> span.setAttribute("tracy.request.size", content.orRedactedInput())
                "response_format" -> span.setAttribute("tracy.request.response_format", content.orRedactedInput())
                "image" -> {
                    span.setAttribute("tracy.request.image.size_bytes", part.content.size.toLong())
                    if (contentTracingAllowed(ContentKind.INPUT)) {
                        span.setAttribute("gen_ai.request.image.$imagesCount.content", content)
                        span.setAttribute("gen_ai.request.image.$imagesCount.contentType", contentType.asString())
                        if (part.filename != null) {
                            span.setAttribute("gen_ai.request.image.$imagesCount.filename", part.filename)
                        }
                        mediaContentParts.add(MediaContentPart(resource = Resource.Base64(content, contentType.asString())))
                        ++imagesCount
                    }
                }
                null -> logger.warn { "Form data part with missing name ignored. Content type: '$contentType'" }
                else -> span.setAttribute("tracy.request.${part.name}", content.orRedactedInput())
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

    override fun handleStreaming(span: Span, events: String) {
        for (line in events.lineSequence()) {
            if (!line.startsWith("data:")) continue
            val data = try {
                Json.parseToJsonElement(line.removePrefix("data:").trim()).jsonObject
            } catch (err: Exception) {
                logger.trace("Failed to parse streaming data: '$line'", err)
                null
            } ?: continue

            handleStreamedImage(
                span, data, extractor,
                completedType = "image_generation.completed",
                partialImageType = "image_generation.partial_image",
            )
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
