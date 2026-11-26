/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.anthropic.adapters.handlers.batches

import io.opentelemetry.api.trace.Span
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_OPERATION_NAME
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.ai.tracy.anthropic.adapters.handlers.batches.routes.CancelBatchHandler
import org.jetbrains.ai.tracy.anthropic.adapters.handlers.batches.routes.CreateBatchHandler
import org.jetbrains.ai.tracy.anthropic.adapters.handlers.batches.routes.DeleteBatchHandler
import org.jetbrains.ai.tracy.anthropic.adapters.handlers.batches.routes.ListBatchesHandler
import org.jetbrains.ai.tracy.anthropic.adapters.handlers.batches.routes.RetrieveBatchHandler
import org.jetbrains.ai.tracy.anthropic.adapters.handlers.batches.routes.RetrieveBatchResultsHandler
import org.jetbrains.ai.tracy.core.adapters.handlers.EndpointApiHandler
import org.jetbrains.ai.tracy.core.adapters.handlers.RouteHandler
import org.jetbrains.ai.tracy.core.adapters.handlers.sse.sseHandlingUnsupported
import org.jetbrains.ai.tracy.core.http.parsers.SseEvent
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpRequest
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpResponse
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpUrl

/**
 * Handler for Anthropic Message Batches API.
 *
 * Maps HTTP method + URL path to `gen_ai.operation.name`:
 * - `POST   /v1/messages/batches`                 → `"batches.create"`
 * - `GET    /v1/messages/batches`                 → `"batches.list"`
 * - `GET    /v1/messages/batches/{id}`            → `"batches.retrieve"`
 * - `POST   /v1/messages/batches/{id}/cancel`     → `"batches.cancel"`
 * - `DELETE /v1/messages/batches/{id}`            → `"batches.delete"`
 * - `GET    /v1/messages/batches/{id}/results`    → `"batches.results"`
 *
 * Dispatches to per-route [RouteHandler] implementations under `batches/routes/`.
 *
 * See: [Message Batches API](https://docs.anthropic.com/en/api/creating-message-batches)
 */
internal class BatchesAnthropicApiEndpointHandler : EndpointApiHandler {

    private val routeHandlers: Map<BatchRoute, RouteHandler> by lazy {
        mapOf(
            BatchRoute.CREATE to CreateBatchHandler(),
            BatchRoute.LIST to ListBatchesHandler(),
            BatchRoute.RETRIEVE to RetrieveBatchHandler(),
            BatchRoute.CANCEL to CancelBatchHandler(),
            BatchRoute.DELETE to DeleteBatchHandler(),
            BatchRoute.RESULTS to RetrieveBatchResultsHandler(),
        )
    }

    override fun handleRequestAttributes(span: Span, request: TracyHttpRequest) {
        span.setAttribute("anthropic.api.type", "batches")

        val route = detectRoute(request.url, request.method)
        span.setAttribute(GEN_AI_OPERATION_NAME, route.operationName)

        routeHandlers[route]?.handleRequest(span, request)
    }

    override fun handleResponseAttributes(span: Span, response: TracyHttpResponse) {
        if (response.isError()) return
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

    /**
     * Detects which batch operation is being called based on the URL path and HTTP method.
     * Exposed as `internal` so the adapter's error path can recover the operation name
     * even after a redirect rewrites the response method.
     */
    internal fun detectOperation(url: TracyHttpUrl, method: String): String =
        detectRoute(url, method).operationName

    private fun detectRoute(url: TracyHttpUrl, method: String): BatchRoute {
        val segments = url.pathSegments
        val batchesIndex = segments.indexOf("batches")
        if (batchesIndex == -1) {
            logger.warn { "No 'batches' segment in URL path: ${segments.joinToString("/")}" }
            return BatchRoute.CREATE
        }

        val afterBatches = segments.drop(batchesIndex + 1).filter { it.isNotBlank() }
        val hasBatchId = afterBatches.isNotEmpty() && afterBatches.first() != "cancel"
        val hasCancel = afterBatches.contains("cancel")
        val hasResults = afterBatches.contains("results")

        return when {
            method == "POST" && !hasBatchId && !hasCancel -> BatchRoute.CREATE
            method == "GET" && !hasBatchId -> BatchRoute.LIST
            method == "GET" && hasBatchId && hasResults -> BatchRoute.RESULTS
            method == "GET" && hasBatchId -> BatchRoute.RETRIEVE
            method == "POST" && hasBatchId && hasCancel -> BatchRoute.CANCEL
            method == "DELETE" && hasBatchId -> BatchRoute.DELETE
            else -> {
                logger.warn { "Unknown batch operation: $method /${segments.joinToString("/")}" }
                BatchRoute.CREATE
            }
        }
    }

    private enum class BatchRoute(val operationName: String) {
        CREATE("batches.create"),
        LIST("batches.list"),
        RETRIEVE("batches.retrieve"),
        CANCEL("batches.cancel"),
        DELETE("batches.delete"),
        RESULTS("batches.results"),
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
