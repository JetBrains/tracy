/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.openai.adapters.handlers.videos.routes

import io.opentelemetry.api.trace.Span
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.ai.tracy.core.adapters.handlers.RouteHandler
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpRequest
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpResponse

private val logger = KotlinLogging.logger {}

/**
 * Handles the `GET /videos/{video_id}/content` endpoint.
 */
internal class GetVideoContentHandler : RouteHandler {
    /**
     * Request: Path parameter video_id, query parameter variant
     */
    override fun handleRequest(span: Span, request: TracyHttpRequest) {
        val videoId = extractVideoIdFromPath(request.url)
        if (videoId != null) {
            span.setAttribute("gen_ai.request.video.requested_id", videoId)
        } else {
            logger.warn { "Failed to extract video ID from URL: ${request.url}" }
        }

        request.url.parameters.queryParameter("variant")?.let {
            span.setAttribute("gen_ai.request.variant", it)
        }
    }

    /**
     * Response: Binary video stream (trace metadata only)
     */
    override fun handleResponse(span: Span, response: TracyHttpResponse) {
        // binary stream response -> trace metadata only
        val contentType = response.contentType
        if (contentType != null) {
            span.setAttribute("gen_ai.response.content_type", contentType.asString())
        }
        if (contentType?.type == "video") {
            span.setAttribute("gen_ai.response.is_binary_stream", true)
        }
    }
}
