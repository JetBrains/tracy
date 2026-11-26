/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.openai.adapters.handlers.files

import io.opentelemetry.api.trace.Span
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_OPERATION_NAME
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.ai.tracy.core.adapters.handlers.EndpointApiHandler
import org.jetbrains.ai.tracy.core.adapters.handlers.RouteHandler
import org.jetbrains.ai.tracy.core.adapters.handlers.sse.sseHandlingUnsupported
import org.jetbrains.ai.tracy.core.adapters.media.MediaContentExtractor
import org.jetbrains.ai.tracy.core.http.parsers.SseEvent
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpRequest
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpResponse
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpUrl
import org.jetbrains.ai.tracy.openai.adapters.handlers.files.routes.CreateFileHandler
import org.jetbrains.ai.tracy.openai.adapters.handlers.files.routes.DeleteFileHandler
import org.jetbrains.ai.tracy.openai.adapters.handlers.files.routes.GetFileContentHandler
import org.jetbrains.ai.tracy.openai.adapters.handlers.files.routes.ListFilesHandler
import org.jetbrains.ai.tracy.openai.adapters.handlers.files.routes.RetrieveFileHandler

/**
 * Handler for OpenAI Files API.
 *
 * Dispatches to per-route [RouteHandler] implementations under `files/routes/`:
 * 1. `POST   /files`                 → `"files.create"`
 * 2. `GET    /files`                 → `"files.list"`
 * 3. `GET    /files/{file_id}`       → `"files.retrieve"`
 * 4. `DELETE /files/{file_id}`       → `"files.delete"`
 * 5. `GET    /files/{file_id}/content`→ `"files.content"`
 *
 * The main handler re-applies the operation name in the response phase to override what
 * [org.jetbrains.ai.tracy.openai.adapters.handlers.OpenAIApiUtils.setCommonResponseAttributes]
 * sets from the response `object` field (e.g. `"file"`, `"list"`, `"file.deleted"`).
 *
 * See [Files API Reference](https://platform.openai.com/docs/api-reference/files)
 */
internal class FilesOpenAIApiEndpointHandler(
    private val extractor: MediaContentExtractor,
) : EndpointApiHandler {

    private val routeHandlers: Map<FileRoute, RouteHandler> by lazy {
        mapOf(
            FileRoute.CREATE to CreateFileHandler(extractor),
            FileRoute.LIST to ListFilesHandler(),
            FileRoute.RETRIEVE to RetrieveFileHandler(),
            FileRoute.DELETE to DeleteFileHandler(),
            FileRoute.CONTENT to GetFileContentHandler(),
        )
    }

    override fun handleRequestAttributes(span: Span, request: TracyHttpRequest) {
        span.setAttribute("openai.api.type", "files")
        val route = detectRoute(request.url, request.method)
        routeHandlers[route]?.handleRequest(span, request)
    }

    override fun handleResponseAttributes(span: Span, response: TracyHttpResponse) {
        val route = detectRoute(response.url, response.requestMethod)
        span.setAttribute(GEN_AI_OPERATION_NAME, route.operationName)
        routeHandlers[route]?.handleResponse(span, response)
    }

    override fun handleStreamingEvent(
        span: Span,
        event: SseEvent,
        index: Long
    ): Result<Unit> {
        return sseHandlingUnsupported()
    }

    private fun detectRoute(url: TracyHttpUrl, method: String): FileRoute {
        val segments = url.pathSegments
        val filesIndex = segments.indexOf("files")

        if (filesIndex == -1) {
            logger.warn { "Failed to detect files route. Endpoint has no `files` path segment: ${url.pathSegments.joinToString(separator = "/")}" }
            return FileRoute.CREATE
        }

        val hasFileId = segments.size > (filesIndex + 1) &&
                segments[filesIndex + 1].isNotBlank()
        val hasContent = segments.contains("content")

        return when {
            method == "POST" && !hasFileId -> FileRoute.CREATE
            method == "GET" && !hasFileId -> FileRoute.LIST
            method == "GET" && hasFileId && hasContent -> FileRoute.CONTENT
            method == "GET" && hasFileId -> FileRoute.RETRIEVE
            method == "DELETE" && hasFileId -> FileRoute.DELETE
            else -> {
                logger.warn { "Failed to detect files route: $method ${url.pathSegments.joinToString(separator = "/")}" }
                FileRoute.CREATE
            }
        }
    }

    private enum class FileRoute(val operationName: String) {
        CREATE("files.create"),
        LIST("files.list"),
        RETRIEVE("files.retrieve"),
        DELETE("files.delete"),
        CONTENT("files.content"),
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
