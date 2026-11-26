/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.anthropic.adapters.handlers.files

import io.opentelemetry.api.trace.Span
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_OPERATION_NAME
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.ai.tracy.anthropic.adapters.handlers.files.routes.CreateFileHandler
import org.jetbrains.ai.tracy.anthropic.adapters.handlers.files.routes.DeleteFileHandler
import org.jetbrains.ai.tracy.anthropic.adapters.handlers.files.routes.GetFileContentHandler
import org.jetbrains.ai.tracy.anthropic.adapters.handlers.files.routes.ListFilesHandler
import org.jetbrains.ai.tracy.anthropic.adapters.handlers.files.routes.RetrieveFileHandler
import org.jetbrains.ai.tracy.core.adapters.handlers.EndpointApiHandler
import org.jetbrains.ai.tracy.core.adapters.handlers.RouteHandler
import org.jetbrains.ai.tracy.core.adapters.handlers.sse.sseHandlingUnsupported
import org.jetbrains.ai.tracy.core.adapters.media.MediaContentExtractor
import org.jetbrains.ai.tracy.core.http.parsers.SseEvent
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpRequest
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpResponse
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpUrl

/**
 * Handler for Anthropic Files API.
 *
 * Maps HTTP method + URL path to `gen_ai.operation.name`:
 * - `POST   /v1/files`                    → `"files.create"`
 * - `GET    /v1/files`                    → `"files.list"`
 * - `GET    /v1/files/{file_id}`          → `"files.retrieve"`
 * - `DELETE /v1/files/{file_id}`          → `"files.delete"`
 * - `GET    /v1/files/{file_id}/content`  → `"files.content"`
 *
 * Dispatches to per-route [RouteHandler] implementations under `files/routes/`.
 *
 * See: [Files API](https://platform.claude.com/docs/en/api/beta/files)
 */
internal class FilesAnthropicApiEndpointHandler(
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
        span.setAttribute("anthropic.api.type", "files")
        val route = detectRoute(request.url, request.method)
        span.setAttribute(GEN_AI_OPERATION_NAME, route.operationName)
        routeHandlers[route]?.handleRequest(span, request)
    }

    override fun handleResponseAttributes(span: Span, response: TracyHttpResponse) {
        val route = detectRoute(response.url, response.requestMethod)
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
            logger.warn { "No 'files' segment in URL path: ${segments.joinToString("/")}" }
            return FileRoute.CREATE
        }

        val afterFiles = segments.drop(filesIndex + 1).filter { it.isNotBlank() }
        val hasFileId = afterFiles.isNotEmpty() && afterFiles.first() != "content"
        val hasContent = afterFiles.contains("content")

        return when {
            method == "POST" && !hasFileId && !hasContent -> FileRoute.CREATE
            method == "GET" && !hasFileId && !hasContent -> FileRoute.LIST
            method == "GET" && hasFileId && hasContent -> FileRoute.CONTENT
            method == "GET" && hasFileId -> FileRoute.RETRIEVE
            method == "DELETE" && hasFileId -> FileRoute.DELETE
            else -> {
                logger.warn { "Unknown files operation: $method /${segments.joinToString("/")}" }
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
