/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.openai.adapters.handlers.videos.routes

import io.opentelemetry.api.trace.Span
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpRequest
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpResponse
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpUrl
import org.jetbrains.ai.tracy.core.policy.orRedactedOutput

/**
 * Handles requests and responses for different video API routes of OpenAI.
 */
internal interface VideoRouteHandler {
    fun handleRequest(span: Span, request: TracyHttpRequest)
    fun handleResponse(span: Span, response: TracyHttpResponse)
}

/**
 * Extracts `video_id` from a path like `/v1/videos/{video_id}` or `/v1/videos/{video_id}/content`.
 */
internal fun extractVideoIdFromPath(url: TracyHttpUrl): String? {
    val segments = url.pathSegments
    val videosIndex = segments.indexOf("videos")

    return if (videosIndex != -1 && segments.size > videosIndex + 1) {
        val potentialId = segments[videosIndex + 1]
        if (potentialId.isNotBlank() && potentialId != "videos") {
            potentialId
        } else {
            null
        }
    } else {
        null
    }
}


/**
 * Traces a Video model object with all its fields.
 *
 * Video schema:
 * - id: string
 * - completed_at: number
 * - created_at: number
 * - error: VideoCreateError
 * - expires_at: number
 * - model: string
 * - object: "video"
 * - progress: number
 * - prompt: string
 * - remixed_from_video_id: string
 * - seconds: string
 * - size: string
 * - status: string
 */
internal fun Span.traceVideoModel(video: JsonObject, prefix: String) {
    val span = this

    video["id"]?.let {
        span.setAttribute("$prefix.id", it.jsonPrimitive.content)
    }

    video["status"]?.let {
        span.setAttribute("$prefix.status", it.jsonPrimitive.content)
    }

    video["progress"]?.let {
        span.setAttribute("$prefix.progress", it.jsonPrimitive.content)
    }

    video["prompt"]?.let {
        span.setAttribute("$prefix.prompt", it.jsonPrimitive.content.orRedactedOutput())
    }

    video["model"]?.let {
        span.setAttribute("$prefix.model", it.jsonPrimitive.content)
    }

    video["seconds"]?.let {
        span.setAttribute("$prefix.seconds", it.jsonPrimitive.content)
    }

    video["size"]?.let {
        span.setAttribute("$prefix.size", it.jsonPrimitive.content)
    }

    video["object"]?.let {
        span.setAttribute("$prefix.object", it.jsonPrimitive.content)
    }

    video["created_at"]?.let {
        span.setAttribute("$prefix.created_at", it.jsonPrimitive.content)
    }

    video["completed_at"]?.let {
        span.setAttribute("$prefix.completed_at", it.jsonPrimitive.content)
    }

    video["expires_at"]?.let {
        span.setAttribute("$prefix.expires_at", it.jsonPrimitive.content)
    }

    video["remixed_from_video_id"]?.let {
        span.setAttribute("$prefix.remixed_from_video_id", it.jsonPrimitive.content)
    }

    // Trace error if present (either null or JSON object)
    val error = video["error"]
    if (error is JsonObject) {
        span.traceVideoError(error, prefix)
    }
}

/**
 * Traces VideoCreateError object.
 *
 * VideoCreateError schema:
 * - code: string
 * - message: string
 */
internal fun Span.traceVideoError(error: JsonObject, prefix: String) {
    val span = this
    error["code"]?.let {
        span.setAttribute("$prefix.error.code", it.jsonPrimitive.content)
    }
    error["message"]?.let {
        span.setAttribute("$prefix.error.message", it.jsonPrimitive.content)
    }
}
