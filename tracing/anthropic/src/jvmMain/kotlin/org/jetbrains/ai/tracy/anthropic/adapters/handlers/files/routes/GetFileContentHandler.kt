/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.anthropic.adapters.handlers.files.routes

import io.opentelemetry.api.trace.Span
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.ai.tracy.core.adapters.handlers.RouteHandler
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpRequest
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpResponse

/**
 * Handles the `GET /v1/files/{file_id}/content` endpoint. Response body is binary.
 */
internal class GetFileContentHandler : RouteHandler {
    override fun handleRequest(span: Span, request: TracyHttpRequest) {
        // URL: /v1/files/{file_id}/content
        // dropping `content` segment to extract `file_id`
        val fileId = request.url.pathSegments.dropLast(1).lastOrNull()
        if (fileId == null) {
            logger.warn { "No file_id in URL path: ${request.url.pathSegments.joinToString("/")}" }
        }
        span.setAttribute("gen_ai.request.file_id", fileId)
    }

    override fun handleResponse(span: Span, response: TracyHttpResponse) {
        // Binary response body; no JSON attributes to extract.
        // TODO: support responses with (binary) file to trace size and MIME type
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
