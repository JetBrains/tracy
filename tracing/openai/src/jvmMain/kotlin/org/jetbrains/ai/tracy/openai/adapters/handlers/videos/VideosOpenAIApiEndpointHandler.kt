/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.openai.adapters.handlers.videos

import io.opentelemetry.api.trace.Span
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_OPERATION_NAME
import mu.KotlinLogging
import org.jetbrains.ai.tracy.core.adapters.handlers.EndpointApiHandler
import org.jetbrains.ai.tracy.core.adapters.media.MediaContentExtractor
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpRequest
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpResponse
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpUrl
import org.jetbrains.ai.tracy.openai.adapters.handlers.videos.routes.*

/**
 * Handler for OpenAI Videos API (Sora video generation).
 *
 * The Videos API provides multiple endpoints for video operations:
 * 1. `POST /videos` - Create video generation job (returns Video with initial status)
 * 2. `GET /videos/{video_id}` - Get video job status (returns Video)
 * 3. `GET /videos` - List all videos with pagination (returns array of Videos)
 * 4. `DELETE /videos/{video_id}` - Delete video (returns deletion confirmation)
 * 5. `GET /videos/{video_id}/content` - Download video MP4 (returns binary stream)
 * 6. `POST /videos/{video_id}/remix` - Remix existing video (returns new Video)
 *
 * This handler detects the specific route and traces accordingly.
 *
 * See [Videos API Reference](https://platform.openai.com/docs/api-reference/videos)
 */
internal class VideosOpenAIApiEndpointHandler(
    private val extractor: MediaContentExtractor
) : EndpointApiHandler {
    /**
     * Registry of route handlers, initialized lazily to avoid creating handlers until needed.
     */
    private val routeHandlers: Map<VideoRoute, VideoRouteHandler> by lazy {
        mapOf(
            VideoRoute.CREATE to CreateVideoHandler(extractor),
            VideoRoute.GET_VIDEO to GetVideoHandler(),
            VideoRoute.DELETE to DeleteVideoHandler(),
            VideoRoute.LIST to ListVideosHandler(),
            VideoRoute.VIDEO_CONTENT to GetVideoContentHandler(),
            VideoRoute.REMIX to RemixVideoHandler()
        )
    }

    override fun handleRequestAttributes(span: Span, request: TracyHttpRequest) {
        val route = detectRoute(request.url, request.method)
        span.setAttribute("openai.api.type", "videos")
        span.setAttribute(GEN_AI_OPERATION_NAME, route.operationName)
        routeHandlers[route]?.handleRequest(span, request)
    }

    override fun handleResponseAttributes(span: Span, response: TracyHttpResponse) {
        val route = detectRoute(response.url, response.requestMethod)
        routeHandlers[route]?.handleResponse(span, response)
    }

    override fun handleStreaming(span: Span, events: String) {
        // Videos API doesn't support SSE streaming for creation
        // Content download is binary streaming handled separately
        logger.warn { "Videos API does not use server-sent events streaming" }
    }

    /**
     * Detects which specific video endpoint is being called based on the URL path and HTTP method.
     */
    private fun detectRoute(url: TracyHttpUrl, method: String): VideoRoute {
        val segments = url.pathSegments
        // find index of "videos" segment
        val videosIndex = segments.indexOf("videos")
        if (videosIndex == -1) {
            // fallback
            logger.warn { "Failed to detect video route. Endpoint has no `videos` path segment: ${url.pathSegments.joinToString(separator = "/")}" }
            return VideoRoute.CREATE
        }
        val containsVideoId = segments.size > (videosIndex + 1) &&
                    segments[videosIndex + 1].isNotBlank() &&
                    segments[videosIndex + 1] != "videos"

        return when {
            method == "POST" && !containsVideoId -> VideoRoute.CREATE
            method == "POST" && segments.contains("remix") -> VideoRoute.REMIX
            method == "GET" && segments.contains("content") -> VideoRoute.VIDEO_CONTENT
            method == "GET" && containsVideoId -> VideoRoute.GET_VIDEO
            method == "GET" && !containsVideoId -> VideoRoute.LIST
            method == "DELETE" && containsVideoId -> VideoRoute.DELETE
            // fallback
            else -> {
                logger.warn { "Failed to detect video route: $method ${url.pathSegments.joinToString(separator = "/")}" }
                VideoRoute.CREATE
            }
        }
    }

    /**
     * Internal enum to distinguish between different video API routes.
     */
    private enum class VideoRoute(val operationName: String) {
        CREATE("videos.create"),
        GET_VIDEO("videos.retrieve"),
        DELETE("videos.delete"),
        LIST("videos.list"),
        VIDEO_CONTENT("videos.content"),
        REMIX("videos.remix")
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
