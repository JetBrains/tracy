/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.openai.adapters.handlers.videos.routes

import io.opentelemetry.api.trace.Span
import kotlinx.serialization.json.jsonObject
import mu.KotlinLogging
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpRequest
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpResponse
import org.jetbrains.ai.tracy.core.http.protocol.asFormData
import org.jetbrains.ai.tracy.core.http.protocol.asJson
import org.jetbrains.ai.tracy.core.policy.orRedactedInput
import org.jetbrains.ai.tracy.openai.adapters.handlers.videos.VideosOpenAIApiEndpointHandler

/**
 * Handles [VideosOpenAIApiEndpointHandler.VideoRoute.REMIX] endpoint: `POST /videos/{video_id}/remix`.
 */
internal class RemixVideoHandler : VideoRouteHandler {
    /**
     * Request: Path parameter video_id, body with prompt
     */
    override fun handleRequest(span: Span, request: TracyHttpRequest) {
        val videoId = extractVideoIdFromPath(request.url)
        if (videoId != null) {
            span.setAttribute("gen_ai.request.video.requested_id", videoId)
        } else {
            logger.warn { "Failed to extract video ID from URL: ${request.url}" }
        }

        val body = request.body.asFormData() ?: return

        for (part in body.parts) {
            val contentType = part.contentType
            if (contentType == null) {
                logger.warn { "Missing content type of form data part '${part.name}'" }
                continue
            }

            // decode content based on the expected content type
            val content = when(contentType.type) {
                "text" -> part.content.toString(contentType.charset() ?: Charsets.US_ASCII)
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
                null -> {
                    logger.warn { "Form data part with missing name ignored. Content type: '$contentType'" }
                }
                else -> {
                    // since we don't know how sensitive other fields may be,
                    // we disguise their content if input tracing is disallowed.
                    span.setAttribute("gen_ai.request.${part.name}", content.orRedactedInput())
                }
            }
        }
    }

    /**
     * Response: Video model (new remixed video)
     */
    override fun handleResponse(span: Span, response: TracyHttpResponse) {
        val body = response.body.asJson()?.jsonObject ?: return
        span.traceVideoModel(body, "gen_ai.response.video")
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}