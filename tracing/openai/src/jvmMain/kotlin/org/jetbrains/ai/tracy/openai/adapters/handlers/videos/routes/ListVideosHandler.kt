/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.openai.adapters.handlers.videos.routes

import io.opentelemetry.api.trace.Span
import kotlinx.serialization.json.*
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpRequest
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpResponse
import org.jetbrains.ai.tracy.core.http.protocol.asJson
import org.jetbrains.ai.tracy.openai.adapters.handlers.videos.VideosOpenAIApiEndpointHandler

/**
 * Handles [VideosOpenAIApiEndpointHandler.VideoRoute.LIST] endpoint: `GET /videos`.
 */
internal class ListVideosHandler : VideoRouteHandler {
    /**
     * Request: Query parameters after, limit, order
     */
    override fun handleRequest(span: Span, request: TracyHttpRequest) {
        val params = request.url.parameters
        params.queryParameter("after")?.let { span.setAttribute("tracy.request.after", it) }
        params.queryParameter("limit")?.toLongOrNull()?.let { span.setAttribute("tracy.request.limit", it) }
        params.queryParameter("order")?.let { span.setAttribute("tracy.request.order", it) }
    }

    /**
     * Response: { data: Video[], first_id, last_id, has_more, object }
     */
    override fun handleResponse(span: Span, response: TracyHttpResponse) {
        val body = response.body.asJson()?.jsonObject ?: return

        body["object"]?.jsonPrimitive?.contentOrNull?.let { span.setAttribute("tracy.response.object", it) }
        body["first_id"]?.jsonPrimitive?.contentOrNull?.let { span.setAttribute("tracy.response.first_id", it) }
        body["last_id"]?.jsonPrimitive?.contentOrNull?.let { span.setAttribute("tracy.response.last_id", it) }
        body["has_more"]?.jsonPrimitive?.booleanOrNull?.let { span.setAttribute("tracy.response.has_more", it) }

        val data = body["data"]
        if (data != null && data is JsonArray) {
            span.setAttribute("tracy.response.videos.count", data.size.toLong())
        }
    }
}