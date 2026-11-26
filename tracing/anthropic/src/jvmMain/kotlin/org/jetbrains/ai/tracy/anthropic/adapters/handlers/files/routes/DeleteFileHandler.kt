/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.anthropic.adapters.handlers.files.routes

import io.opentelemetry.api.trace.Span
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_RESPONSE_ID
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.ai.tracy.core.adapters.handlers.RouteHandler
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpRequest
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpResponse
import org.jetbrains.ai.tracy.core.http.protocol.asJson

/**
 * Handles the `DELETE /v1/files/{file_id}` endpoint.
 *
 * See [files/delete](https://platform.claude.com/docs/en/api/beta/files/delete)
 */
internal class DeleteFileHandler : RouteHandler {
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
        body["id"]?.jsonPrimitive?.content?.let { id ->
            span.setAttribute(GEN_AI_RESPONSE_ID, id)
            span.setAttribute("gen_ai.response.file.id", id)
        }
        body["type"]?.jsonPrimitive?.content?.let {
            span.setAttribute("gen_ai.response.file.type", it)
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
