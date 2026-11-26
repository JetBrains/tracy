/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.openai.adapters.handlers.batches

import io.opentelemetry.api.trace.Span
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_OPERATION_NAME
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.ai.tracy.core.adapters.handlers.EndpointApiHandler
import org.jetbrains.ai.tracy.core.adapters.handlers.RouteHandler
import org.jetbrains.ai.tracy.core.adapters.handlers.sse.sseHandlingUnsupported
import org.jetbrains.ai.tracy.core.http.parsers.SseEvent
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpRequest
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpResponse
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpUrl
import org.jetbrains.ai.tracy.openai.adapters.handlers.batches.routes.CancelBatchHandler
import org.jetbrains.ai.tracy.openai.adapters.handlers.batches.routes.CreateBatchHandler
import org.jetbrains.ai.tracy.openai.adapters.handlers.batches.routes.ListBatchesHandler
import org.jetbrains.ai.tracy.openai.adapters.handlers.batches.routes.RetrieveBatchHandler

/**
 * Handler for OpenAI Batches API.
 *
 * The Batches API provides endpoints to create and manage asynchronous batch jobs:
 * 1. `POST /batches`                  → `"batches.create"`
 * 2. `GET  /batches/{batch_id}`       → `"batches.retrieve"`
 * 3. `POST /batches/{batch_id}/cancel`→ `"batches.cancel"`
 * 4. `GET  /batches`                  → `"batches.list"`
 *
 * Dispatches to per-route [RouteHandler] implementations under `batches/routes/`.
 * The main handler re-applies the operation name in the response phase to override
 * what [org.jetbrains.ai.tracy.openai.adapters.handlers.OpenAIApiUtils.setCommonResponseAttributes]
 * sets from the response `object` field (e.g. `"batch"`, `"list"`).
 *
 * See [Batch API Reference](https://platform.openai.com/docs/api-reference/batch)
 */
internal class BatchesOpenAIApiEndpointHandler : EndpointApiHandler {

    private val routeHandlers: Map<BatchRoute, RouteHandler> by lazy {
        mapOf(
            BatchRoute.CREATE to CreateBatchHandler(),
            BatchRoute.LIST to ListBatchesHandler(),
            BatchRoute.RETRIEVE to RetrieveBatchHandler(),
            BatchRoute.CANCEL to CancelBatchHandler(),
        )
    }

    override fun handleRequestAttributes(span: Span, request: TracyHttpRequest) {
        span.setAttribute("openai.api.type", "batches")
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

    private fun detectRoute(url: TracyHttpUrl, method: String): BatchRoute {
        val segments = url.pathSegments
        val batchesIndex = segments.indexOf("batches")

        if (batchesIndex == -1) {
            logger.warn { "Failed to detect batches route. Endpoint has no `batches` path segment: ${url.pathSegments.joinToString(separator = "/")}" }
            return BatchRoute.CREATE
        }

        val hasBatchId = segments.size > (batchesIndex + 1) && segments[batchesIndex + 1].isNotBlank()
        val hasCancel = segments.contains("cancel")

        return when {
            method == "POST" && !hasBatchId -> BatchRoute.CREATE
            method == "GET" && !hasBatchId -> BatchRoute.LIST
            method == "GET" && hasBatchId -> BatchRoute.RETRIEVE
            method == "POST" && hasBatchId && hasCancel -> BatchRoute.CANCEL
            else -> {
                logger.warn { "Failed to detect batches route: $method ${url.pathSegments.joinToString(separator = "/")}" }
                BatchRoute.CREATE
            }
        }
    }

    private enum class BatchRoute(val operationName: String) {
        CREATE("batches.create"),
        LIST("batches.list"),
        RETRIEVE("batches.retrieve"),
        CANCEL("batches.cancel"),
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
