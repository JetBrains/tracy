/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.anthropic.adapters.handlers.models

import io.opentelemetry.api.trace.Span
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_OPERATION_NAME
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.ai.tracy.anthropic.adapters.handlers.models.routes.ListModelsHandler
import org.jetbrains.ai.tracy.anthropic.adapters.handlers.models.routes.RetrieveModelHandler
import org.jetbrains.ai.tracy.core.adapters.handlers.EndpointApiHandler
import org.jetbrains.ai.tracy.core.adapters.handlers.RouteHandler
import org.jetbrains.ai.tracy.core.adapters.handlers.sse.sseHandlingUnsupported
import org.jetbrains.ai.tracy.core.http.parsers.SseEvent
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpRequest
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpResponse
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpUrl

/**
 * Handler for Anthropic Models API.
 *
 * Maps HTTP method + URL path to `gen_ai.operation.name`:
 * - `GET /v1/models`              → `"list"`
 * - `GET /v1/models/{model_id}`   → `"retrieve"`
 *
 * Dispatches to per-route [RouteHandler] implementations under `models/routes/`.
 *
 * See: [Models API](https://docs.anthropic.com/en/api/models-list)
 */
internal class ModelsAnthropicApiEndpointHandler : EndpointApiHandler {

    private val routeHandlers: Map<ModelRoute, RouteHandler> by lazy {
        mapOf(
            ModelRoute.LIST to ListModelsHandler(),
            ModelRoute.RETRIEVE to RetrieveModelHandler(),
        )
    }

    override fun handleRequestAttributes(span: Span, request: TracyHttpRequest) {
        span.setAttribute("anthropic.api.type", "models")
        val route = detectRoute(request.url)
        span.setAttribute(GEN_AI_OPERATION_NAME, route.operationName)
        routeHandlers[route]?.handleRequest(span, request)
    }

    override fun handleResponseAttributes(span: Span, response: TracyHttpResponse) {
        val route = detectRoute(response.url)
        routeHandlers[route]?.handleResponse(span, response)
    }

    override fun handleStreamingEvent(
        span: Span,
        event: SseEvent,
        index: Long
    ): Result<Unit> {
        return sseHandlingUnsupported()
    }

    private fun detectRoute(url: TracyHttpUrl): ModelRoute {
        val segments = url.pathSegments
        val modelsIndex = segments.indexOf("models")
        if (modelsIndex == -1) {
            logger.warn { "No 'models' segment in URL path: ${segments.joinToString("/")}" }
            return ModelRoute.LIST
        }
        val after = segments.drop(modelsIndex + 1).filter { it.isNotBlank() }
        return if (after.isNotEmpty()) ModelRoute.RETRIEVE else ModelRoute.LIST
    }

    private enum class ModelRoute(val operationName: String) {
        LIST("list"),
        RETRIEVE("retrieve"),
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
