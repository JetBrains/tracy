/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.openai.adapters.handlers.videos.routes

import io.opentelemetry.api.trace.Span
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpRequest
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpResponse
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
            span.setAttribute("gen_ai.video.source_id", videoId)
        } else {
            logger.warn { "Failed to extract video ID from URL: ${request.url}" }
        }

        val body = request.body.asJson()?.jsonObject ?: return
        body["prompt"]?.let {
            span.setAttribute("gen_ai.prompt.0.content", it.jsonPrimitive.content.orRedactedInput())
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