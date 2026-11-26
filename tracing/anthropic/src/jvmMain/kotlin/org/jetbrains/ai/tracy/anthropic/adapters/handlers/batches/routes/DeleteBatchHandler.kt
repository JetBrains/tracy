/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.anthropic.adapters.handlers.batches.routes

import io.opentelemetry.api.trace.Span
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.ai.tracy.core.adapters.handlers.RouteHandler
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpRequest
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpResponse
import org.jetbrains.ai.tracy.core.http.protocol.asJson

/**
 * Handles the `DELETE /v1/messages/batches/{id}` endpoint.
 */
internal class DeleteBatchHandler : RouteHandler {
    override fun handleRequest(span: Span, request: TracyHttpRequest) {
        // NOTE: No request-side body attributes, only a single path parameter
        val messageBatchId = request.url.pathSegments.lastOrNull()
        if (messageBatchId == null) {
            logger.warn { "No message_batch_id in URL path: ${request.url.pathSegments.joinToString("/")}" }
        }
        span.setAttribute("gen_ai.request.message_batch_id", messageBatchId)
    }

    override fun handleResponse(span: Span, response: TracyHttpResponse) {
        val body = response.body.asJson()?.jsonObject ?: return
        body["id"]?.jsonPrimitive?.content?.let {
            span.setAttribute("gen_ai.response.message_batch_id", it)
        }
        body["type"]?.jsonPrimitive?.content?.let {
            span.setAttribute("gen_ai.response.type", it)
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
