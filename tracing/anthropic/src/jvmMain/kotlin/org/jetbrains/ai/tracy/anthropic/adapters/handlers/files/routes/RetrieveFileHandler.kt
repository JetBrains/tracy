/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.anthropic.adapters.handlers.files.routes

import io.opentelemetry.api.trace.Span
import kotlinx.serialization.json.jsonObject
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.ai.tracy.core.adapters.handlers.RouteHandler
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpRequest
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpResponse
import org.jetbrains.ai.tracy.core.http.protocol.asJson

/**
 * Handles the `GET /v1/files/{file_id}` endpoint.
 *
 * See [files/retrieve](https://platform.claude.com/docs/en/api/beta/files/retrieve)
 */
internal class RetrieveFileHandler : RouteHandler {
    override fun handleRequest(span: Span, request: TracyHttpRequest) {
        // URL: /v1/files/{file_id}
        val fileId = request.url.pathSegments.lastOrNull()
        if (fileId == null) {
            logger.warn { "No file_id in URL path: ${request.url.pathSegments.joinToString("/")}" }
        }
        span.setAttribute("gen_ai.request.file_id", fileId)
    }

    override fun handleResponse(span: Span, response: TracyHttpResponse) {
        val body = response.body.asJson()?.jsonObject ?: return
        span.traceFileMetadata(body)
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
