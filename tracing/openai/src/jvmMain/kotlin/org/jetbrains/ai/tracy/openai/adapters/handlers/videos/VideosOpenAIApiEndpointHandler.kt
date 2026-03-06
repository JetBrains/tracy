/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.openai.adapters.handlers.videos

import io.opentelemetry.api.trace.Span
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_MODEL
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_RESPONSE_ID
import kotlinx.serialization.json.*
import mu.KotlinLogging
import org.jetbrains.ai.tracy.core.adapters.handlers.EndpointApiHandler
import org.jetbrains.ai.tracy.core.adapters.media.MediaContent
import org.jetbrains.ai.tracy.core.adapters.media.MediaContentExtractor
import org.jetbrains.ai.tracy.core.adapters.media.MediaContentPart
import org.jetbrains.ai.tracy.core.adapters.media.Resource
import org.jetbrains.ai.tracy.core.http.protocol.*
import org.jetbrains.ai.tracy.core.policy.ContentKind
import org.jetbrains.ai.tracy.core.policy.contentTracingAllowed
import org.jetbrains.ai.tracy.core.policy.orRedactedInput
import org.jetbrains.ai.tracy.core.policy.orRedactedOutput
import java.util.*

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
    override fun handleRequestAttributes(span: Span, request: TracyHttpRequest) {
        val route = detectRoute(request.url, request.method)

        when (route) {
            VideoRoute.CREATE -> handleCreateRequest(span, request)
            VideoRoute.GET_VIDEO -> handleGetRequest(span, request)
            VideoRoute.LIST -> handleListRequest(span, request)
            VideoRoute.DELETE -> handleDeleteRequest(span, request)
            VideoRoute.VIDEO_CONTENT -> handleContentRequest(span, request)
            VideoRoute.REMIX -> handleRemixRequest(span, request)
        }
    }

    override fun handleResponseAttributes(span: Span, response: TracyHttpResponse) {
        val route = detectRoute(response.url, response.requestMethod)

        when (route) {
            VideoRoute.CREATE, VideoRoute.GET_VIDEO, VideoRoute.REMIX -> handleVideoModelResponse(span, response)
            VideoRoute.LIST -> handleListResponse(span, response)
            VideoRoute.DELETE -> handleDeleteResponse(span, response)
            VideoRoute.VIDEO_CONTENT -> handleContentResponse(span, response)
        }
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
            else -> VideoRoute.CREATE
        }
    }

    // ============ REQUEST HANDLERS ============

    // TODO: move these request/response handlers into smaller classes

    /**
     * Handles `POST /videos` request.
     * Request is `multipart/form-data` with: prompt, input_reference (file), model, seconds, size
     */
    private fun handleCreateRequest(span: Span, request: TracyHttpRequest) {
        val body = request.body.asFormData() ?: return

        val mediaContentParts = mutableListOf<MediaContentPart>()

        for (part in body.parts) {
            val contentType = part.contentType

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
                "input_reference" -> if (contentTracingAllowed(ContentKind.INPUT) && contentType != null) {
                    // TODO: this `has_data` attribute not needed
                    span.setAttribute("gen_ai.request.input_reference.has_data", "true")
                    span.setAttribute("gen_ai.request.input_reference.contentType", contentType.asString())
                    if (part.filename != null) {
                        span.setAttribute("gen_ai.request.input_reference.filename", part.filename)
                    }
                    mediaContentParts.add(
                        MediaContentPart(resource = Resource.Base64(content, contentType.asString()))
                    )
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

    /**
     * Handles GET /videos/{video_id} request.
     * Path parameter: video_id
     */
    private fun handleGetRequest(span: Span, request: TracyHttpRequest) {
        val videoId = extractVideoIdFromPath(request.url)
        if (videoId != null) {
            span.setAttribute("gen_ai.video.requested_id", videoId)
        }
    }

    /**
     * Handles GET /videos request.
     * Query parameters: after, limit, order
     */
    private fun handleListRequest(span: Span, request: TracyHttpRequest) {
        val params = request.url.parameters

        params.queryParameter("after")?.let {
            span.setAttribute("gen_ai.request.after", it.orRedactedInput())
        }
        params.queryParameter("limit")?.let {
            span.setAttribute("gen_ai.request.limit", it)
        }
        params.queryParameter("order")?.let {
            span.setAttribute("gen_ai.request.order", it)
        }
    }

    /**
     * Handles DELETE /videos/{video_id} request.
     * Path parameter: video_id
     */
    private fun handleDeleteRequest(span: Span, request: TracyHttpRequest) {
        val videoId = extractVideoIdFromPath(request.url)
        if (videoId != null) {
            span.setAttribute("gen_ai.video.requested_id", videoId)
        }
    }

    /**
     * Handles GET /videos/{video_id}/content request.
     * Path parameter: video_id
     * Query parameter: variant
     */
    private fun handleContentRequest(span: Span, request: TracyHttpRequest) {
        val videoId = extractVideoIdFromPath(request.url)
        if (videoId != null) {
            span.setAttribute("gen_ai.video.requested_id", videoId)
        }

        request.url.parameters.queryParameter("variant")?.let {
            span.setAttribute("gen_ai.request.variant", it)
        }
    }

    /**
     * Handles POST /videos/{video_id}/remix request.
     * Path parameter: video_id
     * Request body: { prompt: string }
     */
    private fun handleRemixRequest(span: Span, request: TracyHttpRequest) {
        val videoId = extractVideoIdFromPath(request.url)
        if (videoId != null) {
            span.setAttribute("gen_ai.video.source_id", videoId)
        }

        val body = request.body.asJson()?.jsonObject ?: return

        body["prompt"]?.let {
            span.setAttribute("gen_ai.prompt.0.content", it.jsonPrimitive.content.orRedactedInput())
        }
    }

    // ============ RESPONSE HANDLERS ============

    /**
     * Handles response containing a Video model.
     * Used by: CREATE, GET, REMIX endpoints
     */
    private fun handleVideoModelResponse(span: Span, response: TracyHttpResponse) {
        val body = response.body.asJson()?.jsonObject ?: return
        traceVideoModel(span, body, "gen_ai.response.video")
    }

    /**
     * Handles GET /videos response (list of videos).
     * Response: { data: Video[], first_id, last_id, has_more, object }
     */
    private fun handleListResponse(span: Span, response: TracyHttpResponse) {
        val body = response.body.asJson()?.jsonObject ?: return

        body["data"]?.jsonArray?.let { videos ->
            span.setAttribute("gen_ai.response.videos_count", videos.size.toString())

            // Trace each video in the list
            videos.forEachIndexed { index, videoElement ->
                if (videoElement is JsonObject) {
                    traceVideoModel(span, videoElement, "gen_ai.response.videos.$index")
                }
            }
        }

        body["first_id"]?.let {
            span.setAttribute("gen_ai.response.first_id", it.jsonPrimitive.content)
        }
        body["last_id"]?.let {
            span.setAttribute("gen_ai.response.last_id", it.jsonPrimitive.content)
        }
        body["has_more"]?.let {
            span.setAttribute("gen_ai.response.has_more", it.jsonPrimitive.boolean)
        }
        body["object"]?.let {
            span.setAttribute("gen_ai.response.object", it.jsonPrimitive.content)
        }
    }

    /**
     * Handles DELETE /videos/{video_id} response.
     * Response: { id, deleted, object }
     */
    private fun handleDeleteResponse(span: Span, response: TracyHttpResponse) {
        val body = response.body.asJson()?.jsonObject ?: return

        body["id"]?.let {
            // TODO: it's video id! not response id
            span.setAttribute(GEN_AI_RESPONSE_ID, it.jsonPrimitive.content)
        }
        body["deleted"]?.let {
            span.setAttribute("gen_ai.response.deleted", it.jsonPrimitive.boolean)
        }
        body["object"]?.let {
            span.setAttribute("gen_ai.response.object", it.jsonPrimitive.content)
        }
    }

    /**
     * Handles GET /videos/{video_id}/content response (binary video stream).
     * Don't trace binary content, just metadata.
     */
    private fun handleContentResponse(span: Span, response: TracyHttpResponse) {
        // Binary stream response - trace metadata only
        // TODO: correct?
        span.setAttribute("gen_ai.response.content_type", "video/mp4")
        span.setAttribute("gen_ai.response.is_binary_stream", true)
    }

    // ============ SHARED TRACING METHODS ============

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
    private fun traceVideoModel(span: Span, video: JsonObject, prefix: String) {
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

        // Trace error if present
        video["error"]?.jsonObject?.let { error ->
            traceVideoError(span, error, prefix)
        }
    }

    /**
     * Traces VideoCreateError object.
     *
     * VideoCreateError schema:
     * - code: string
     * - message: string
     */
    private fun traceVideoError(span: Span, error: JsonObject, prefix: String) {
        error["code"]?.let {
            span.setAttribute("$prefix.error.code", it.jsonPrimitive.content)
        }
        error["message"]?.let {
            span.setAttribute("$prefix.error.message", it.jsonPrimitive.content)
        }
    }

    /**
     * Extracts video_id from a path like `/v1/videos/{video_id}` or `/v1/videos/{video_id}/content`.
     */
    private fun extractVideoIdFromPath(url: TracyHttpUrl): String? {
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
     * Internal enum to distinguish between different video API routes.
     */
    private enum class VideoRoute {
        CREATE,   // POST /videos
        GET_VIDEO,      // GET /videos/{video_id}
        LIST,     // GET /videos
        DELETE,   // DELETE /videos/{video_id}
        VIDEO_CONTENT,  // GET /videos/{video_id}/content
        REMIX     // POST /videos/{video_id}/remix
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
