/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.openai.adapters.handlers.videos.routes

import io.opentelemetry.api.trace.Span
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_MODEL
import kotlinx.serialization.json.jsonObject
import mu.KotlinLogging
import org.jetbrains.ai.tracy.core.adapters.media.MediaContent
import org.jetbrains.ai.tracy.core.adapters.media.MediaContentExtractor
import org.jetbrains.ai.tracy.core.adapters.media.MediaContentPart
import org.jetbrains.ai.tracy.core.adapters.media.Resource
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpRequest
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpResponse
import org.jetbrains.ai.tracy.core.http.protocol.asFormData
import org.jetbrains.ai.tracy.core.http.protocol.asJson
import org.jetbrains.ai.tracy.core.policy.ContentKind
import org.jetbrains.ai.tracy.core.policy.contentTracingAllowed
import org.jetbrains.ai.tracy.core.policy.orRedactedInput
import org.jetbrains.ai.tracy.openai.adapters.handlers.videos.VideosOpenAIApiEndpointHandler
import java.util.Base64

private val logger = KotlinLogging.logger {}

/**
 * Handles [VideosOpenAIApiEndpointHandler.VideoRoute.CREATE] endpoint: `POST /videos`.
 */
internal class CreateVideoHandler(private val extractor: MediaContentExtractor) : VideoRouteHandler {
    /**
     * Request is `multipart/form-data` with: prompt, input_reference (file), model, seconds, size
     */
    override fun handleRequest(span: Span, request: TracyHttpRequest) {
        val body = request.body.asFormData() ?: return

        val mediaContentParts = mutableListOf<MediaContentPart>()

        for (part in body.parts) {
            val contentType = part.contentType
            // TODO: TRACY-88
            val content = when {
                contentType == null -> part.content.toString(Charsets.UTF_8)
                contentType.type == "text" -> part.content.toString(
                    contentType.charset() ?: Charsets.UTF_8
                )
                contentType.type.startsWith("image") || contentType.type.startsWith("video") -> {
                    Base64.getEncoder().encodeToString(part.content)
                }
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
                    span.setAttribute(GEN_AI_REQUEST_MODEL, content)
                }
                "seconds" -> {
                    span.setAttribute("gen_ai.request.seconds", content.orRedactedInput())
                }
                "size" -> {
                    span.setAttribute("gen_ai.request.size", content.orRedactedInput())
                }
                "input_reference" -> {
                    span.setAttribute("gen_ai.request.input_reference.contentType", contentType?.asString())
                    span.setAttribute("gen_ai.request.input_reference.content", content.orRedactedInput())
                    if (part.filename != null) {
                        span.setAttribute("gen_ai.request.input_reference.filename", part.filename)
                    }

                    // add as media content part for further upload
                    if (contentTracingAllowed(ContentKind.INPUT) && contentType != null) {
                        mediaContentParts.add(
                            MediaContentPart(resource = Resource.Base64(
                                base64 = content,
                                mediaType = contentType.asString(),
                            ))
                        )
                    }
                }
                else -> {
                    span.setAttribute("gen_ai.request.${part.name}", content.orRedactedInput())
                }
            }
        }

        if (mediaContentParts.isNotEmpty() && contentTracingAllowed(ContentKind.INPUT)) {
            extractor.setUploadableContentAttributes(
                span,
                field = "input",
                content = MediaContent(mediaContentParts)
            )
        }
    }

    override fun handleResponse(span: Span, response: TracyHttpResponse) {
        val body = response.body.asJson()?.jsonObject ?: return
        span.traceVideoModel(body, "gen_ai.response.video")
    }
}

