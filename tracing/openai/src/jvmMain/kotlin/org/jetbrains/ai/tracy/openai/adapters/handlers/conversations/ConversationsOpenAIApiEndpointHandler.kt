/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.openai.adapters.handlers.conversations

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
import org.jetbrains.ai.tracy.openai.adapters.handlers.conversations.routes.CreateConversationHandler
import org.jetbrains.ai.tracy.openai.adapters.handlers.conversations.routes.DeleteConversationHandler
import org.jetbrains.ai.tracy.openai.adapters.handlers.conversations.routes.RetrieveConversationHandler
import org.jetbrains.ai.tracy.openai.adapters.handlers.conversations.routes.UpdateConversationHandler
import org.jetbrains.ai.tracy.openai.adapters.handlers.conversations.routes.items.CreateConversationItemsHandler
import org.jetbrains.ai.tracy.openai.adapters.handlers.conversations.routes.items.DeleteConversationItemHandler
import org.jetbrains.ai.tracy.openai.adapters.handlers.conversations.routes.items.ListConversationItemsHandler
import org.jetbrains.ai.tracy.openai.adapters.handlers.conversations.routes.items.RetrieveConversationItemHandler

/**
 * Handler for OpenAI Conversations API.
 *
 * Dispatches to per-route [RouteHandler] implementations under `conversations/routes/`:
 * 1. `POST /conversations`                            → `"conversations.create"`
 * 2. `GET  /conversations/{id}`                       → `"conversations.retrieve"`
 * 3. `POST /conversations/{id}`                       → `"conversations.update"`
 * 4. `DELETE /conversations/{id}`                     → `"conversations.delete"`
 * 5. `POST /conversations/{id}/items`                 → `"conversations.items.create"`
 * 6. `GET  /conversations/{id}/items`                 → `"conversations.items.list"`
 * 7. `GET  /conversations/{id}/items/{item_id}`       → `"conversations.items.retrieve"`
 * 8. `DELETE /conversations/{id}/items/{item_id}`     → `"conversations.items.delete"`
 *
 * See [Conversations API Reference](https://platform.openai.com/docs/api-reference/conversations)
 */
internal class ConversationsOpenAIApiEndpointHandler : EndpointApiHandler {

    private val routeHandlers: Map<ConversationRoute, RouteHandler> by lazy {
        mapOf(
            ConversationRoute.CREATE to CreateConversationHandler(),
            ConversationRoute.RETRIEVE to RetrieveConversationHandler(),
            ConversationRoute.UPDATE to UpdateConversationHandler(),
            ConversationRoute.DELETE to DeleteConversationHandler(),
            ConversationRoute.ITEMS_CREATE to CreateConversationItemsHandler(),
            ConversationRoute.ITEMS_LIST to ListConversationItemsHandler(),
            ConversationRoute.ITEMS_RETRIEVE to RetrieveConversationItemHandler(),
            ConversationRoute.ITEMS_DELETE to DeleteConversationItemHandler(),
        )
    }

    override fun handleRequestAttributes(span: Span, request: TracyHttpRequest) {
        span.setAttribute("openai.api.type", "conversations")
        val route = detectRoute(request.url, request.method)
        routeHandlers[route]?.handleRequest(span, request)
    }

    override fun handleResponseAttributes(span: Span, response: TracyHttpResponse) {
        val route = detectRoute(response.url, response.requestMethod)
        // Override the operation name set by setCommonResponseAttributes from the JSON `object` field.
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

    private fun detectRoute(url: TracyHttpUrl, method: String): ConversationRoute {
        val segments = url.pathSegments
        val conversationsIndex = segments.indexOf("conversations")

        if (conversationsIndex == -1) {
            logger.warn { "Failed to detect conversation route. Endpoint has no `conversations` path segment: ${url.pathSegments.joinToString(separator = "/")}" }
            return ConversationRoute.CREATE
        }

        val hasConvId = segments.size > (conversationsIndex + 1) &&
                segments[conversationsIndex + 1].isNotBlank()

        val itemsIndex = segments.indexOf("items")
        val hasItems = itemsIndex != -1
        val hasItemId = hasItems && segments.size > (itemsIndex + 1) &&
                segments[itemsIndex + 1].isNotBlank()

        return when {
            method == "POST" && !hasConvId -> ConversationRoute.CREATE
            method == "GET" && hasConvId && !hasItems -> ConversationRoute.RETRIEVE
            method == "POST" && hasConvId && !hasItems -> ConversationRoute.UPDATE
            method == "DELETE" && hasConvId && !hasItems -> ConversationRoute.DELETE
            method == "POST" && hasConvId && hasItems && !hasItemId -> ConversationRoute.ITEMS_CREATE
            method == "GET" && hasConvId && hasItems && !hasItemId -> ConversationRoute.ITEMS_LIST
            method == "GET" && hasConvId && hasItems && hasItemId -> ConversationRoute.ITEMS_RETRIEVE
            method == "DELETE" && hasConvId && hasItems && hasItemId -> ConversationRoute.ITEMS_DELETE
            else -> {
                logger.warn { "Failed to detect conversation route: $method ${url.pathSegments.joinToString(separator = "/")}" }
                ConversationRoute.CREATE
            }
        }
    }

    private enum class ConversationRoute(val operationName: String) {
        CREATE("conversations.create"),
        RETRIEVE("conversations.retrieve"),
        UPDATE("conversations.update"),
        DELETE("conversations.delete"),
        ITEMS_CREATE("conversations.items.create"),
        ITEMS_LIST("conversations.items.list"),
        ITEMS_RETRIEVE("conversations.items.retrieve"),
        ITEMS_DELETE("conversations.items.delete"),
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
