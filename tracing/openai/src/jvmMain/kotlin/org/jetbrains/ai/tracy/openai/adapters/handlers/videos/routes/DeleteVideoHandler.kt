/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.openai.adapters.handlers.videos.routes

import io.opentelemetry.api.trace.Span
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_RESPONSE_ID
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpRequest
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpResponse
import org.jetbrains.ai.tracy.core.http.protocol.asJson
import org.jetbrains.ai.tracy.openai.adapters.handlers.videos.VideosOpenAIApiEndpointHandler

/**
 * Handles [VideosOpenAIApiEndpointHandler.VideoRoute.DELETE] endpoint: `DELETE /videos/{video_id}`.
 */
internal class DeleteVideoHandler : VideoRouteHandler {
    /**
     * Request: Path parameter video_id
     */
    override fun handleRequest(
        span: Span,
        request: TracyHttpRequest
    ) {
        val videoId = extractVideoIdFromPath(request.url)
        if (videoId != null) {
            span.setAttribute("gen_ai.request.video.requested_id", videoId)
        }
    }

    /**
     * Response: { id, deleted, object }
     */
    override fun handleResponse(
        span: Span,
        response: TracyHttpResponse
    ) {
        val body = response.body.asJson()?.jsonObject ?: return

        body["id"]?.let {
            span.setAttribute(GEN_AI_RESPONSE_ID, it.jsonPrimitive.content)
        }
        body["deleted"]?.let {
            span.setAttribute("gen_ai.response.deleted", it.jsonPrimitive.boolean)
        }
        body["object"]?.let {
            span.setAttribute("gen_ai.response.object", it.jsonPrimitive.content)
        }
    }
}