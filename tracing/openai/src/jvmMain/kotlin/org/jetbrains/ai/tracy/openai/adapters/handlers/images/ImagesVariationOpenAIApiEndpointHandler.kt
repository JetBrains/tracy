/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.openai.adapters.handlers.images

import io.opentelemetry.api.trace.Span
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_OUTPUT_TYPE
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_MODEL
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
import java.util.Base64

/**
 * Extracts request/response bodies of Image Variation API.
 *
 * See [Image Variation API](https://platform.openai.com/docs/api-reference/images/createVariation)
 */
internal class ImagesVariationOpenAIApiEndpointHandler(
    private val extractor: MediaContentExtractor
) : EndpointApiHandler {
    override fun handleRequestAttributes(span: Span, request: TracyHttpRequest) {
        val body = request.body.asFormData() ?: return
        val mediaContentParts = mutableListOf<MediaContentPart>()

        for (part in body.parts) {
            val contentType = part.contentType
            val content = when {
                contentType == null -> part.content.toString(Charsets.UTF_8)
                contentType.type == "text" -> part.content.toString(contentType.charset() ?: Charsets.UTF_8)
                contentType.type == "image" -> Base64.getEncoder().encodeToString(part.content)
                else -> null
            } ?: continue

            when (part.name) {
                "model" -> span.setAttribute(GEN_AI_REQUEST_MODEL, content)
                "image" -> {
                    span.setAttribute("tracy.request.image.size_bytes", part.content.size.toLong())
                    contentType?.asString()?.let { span.setAttribute("tracy.request.image.content_type", it) }
                    part.filename?.let { span.setAttribute("tracy.request.image.filename", it.orRedactedInput()) }
                    if (contentTracingAllowed(ContentKind.INPUT) && contentType != null) {
                        mediaContentParts.add(MediaContentPart(Resource.Base64(content, contentType.asString())))
                    }
                }
                null -> Unit
                else -> {
                    part.name?.let { name ->
                        when (name) {
                            "n", "partial_images" -> content.toLongOrNull()
                                ?.let { span.setAttribute("tracy.request.$name", it) }
                                ?: span.setAttribute("tracy.request.$name", content)
                            "stream" -> content.toBooleanStrictOrNull()
                                ?.let { span.setAttribute("gen_ai.request.stream", it) }
                                ?: span.setAttribute("tracy.request.$name", content)
                            else -> span.setAttribute("tracy.request.$name", scalarValue(name, content))
                        }
                    }
                }
            }
        }

        if (mediaContentParts.isNotEmpty() && contentTracingAllowed(ContentKind.INPUT)) {
            extractor.setUploadableContentAttributes(span, field = "input", content = MediaContent(mediaContentParts))
        }
        span.setAttribute(GEN_AI_OUTPUT_TYPE, "image")
    }

    override fun handleResponseAttributes(span: Span, response: TracyHttpResponse) {
        handleImageGenerationResponseAttributes(span, response, extractor)
    }

    override fun handleStreaming(span: Span, events: String) = Unit

    private fun scalarValue(name: String, value: String): String =
        if (name == "prompt") value.orRedactedInput() else value
}
