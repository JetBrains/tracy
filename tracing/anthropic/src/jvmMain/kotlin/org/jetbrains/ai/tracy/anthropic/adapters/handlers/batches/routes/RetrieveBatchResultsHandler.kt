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
import org.jetbrains.ai.tracy.core.policy.orRedactedOutput

/**
 * Handles the `GET /v1/messages/batches/{message_batch_id}/results` endpoint.
 *
 * See [batches/results](https://platform.claude.com/docs/en/api/messages/batches/results)
 */
internal class RetrieveBatchResultsHandler : RouteHandler {
    override fun handleRequest(span: Span, request: TracyHttpRequest) {
        // URL: /v1/messages/batches/{message_batch_id}/results
        // dropping `results` segment to extract `message_batch_id`
        val messageBatchId = request.url.pathSegments.dropLast(1).lastOrNull()
        if (messageBatchId == null) {
            logger.warn { "No message_batch_id in URL path: ${request.url.pathSegments.joinToString("/")}" }
        }
        span.setAttribute("gen_ai.request.message_batch_id", messageBatchId)
    }

    override fun handleResponse(span: Span, response: TracyHttpResponse) {
        val body = response.body.asJson()?.jsonObject ?: return
        body["custom_id"]?.jsonPrimitive?.content?.let {
            span.setAttribute("gen_ai.response.custom_id", it)
        }
        val result = body["result"]?.jsonObject ?: return
        result["type"]?.jsonPrimitive?.content?.let {
            span.setAttribute("gen_ai.response.result.type", it)
        }
        result["message"]?.let {
            span.setAttribute("gen_ai.response.result.message", it.toString().orRedactedOutput())
        }
        result["error"]?.let {
            span.setAttribute("gen_ai.response.result.error", it.toString())
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
