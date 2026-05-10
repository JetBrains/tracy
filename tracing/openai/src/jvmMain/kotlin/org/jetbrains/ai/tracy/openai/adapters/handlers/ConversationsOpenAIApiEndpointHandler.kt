/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.openai.adapters.handlers

import org.jetbrains.ai.tracy.core.adapters.LLMTracingAdapter.Companion.PayloadType
import org.jetbrains.ai.tracy.core.adapters.LLMTracingAdapter.Companion.populateUnmappedAttributes
import org.jetbrains.ai.tracy.core.adapters.handlers.EndpointApiHandler
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpRequest
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpResponse
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpUrl
import org.jetbrains.ai.tracy.core.http.protocol.asJson
import io.opentelemetry.api.trace.Span
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_OPERATION_NAME
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Handler for OpenAI Conversations API.
 *
 * See: [Conversations API](https://platform.openai.com/docs/api-reference/conversations)
 */
internal class ConversationsOpenAIApiEndpointHandler : EndpointApiHandler {
    override fun handleRequestAttributes(span: Span, request: TracyHttpRequest) {
        val op = detectConversationsOperation(request.url, request.method)
        span.setAttribute(GEN_AI_OPERATION_NAME, op)
        span.setAttribute("openai.api.type", "conversations")

        // Extract conversation ID from URL path for all item-level operations
        request.url.pathSegments.let { segments ->
            val convIndex = segments.indexOf("conversations")
            if (convIndex != -1 && segments.size > convIndex + 1) {
                val convId = segments[convIndex + 1]
                if (convId.isNotBlank() && !convId.equals("items", ignoreCase = true)) {
                    span.setAttribute("gen_ai.conversation.id", convId)
                }
            }
        }

        // For list operations, read query params
        if (op == "conversations.items.list") {
            request.url.parameters.queryParameter("limit")?.toLongOrNull()?.let {
                span.setAttribute("tracy.request.limit", it)
            }
            request.url.parameters.queryParameter("order")?.let {
                span.setAttribute("tracy.request.order", it)
            }
            request.url.parameters.queryParameter("after")?.let {
                span.setAttribute("tracy.request.after", it)
            }
        }

        val body = request.body.asJson()?.jsonObject ?: return
        span.populateUnmappedAttributes(body, listOf("metadata", "item"), PayloadType.REQUEST)
    }

    override fun handleResponseAttributes(span: Span, response: TracyHttpResponse) {
        val body = response.body.asJson()?.jsonObject ?: return
        val op = detectConversationsOperation(response.url, response.requestMethod)

        when {
            op == "conversations.delete" -> {
                body["id"]?.jsonPrimitive?.contentOrNull?.let { span.setAttribute("gen_ai.conversation.id", it) }
                body["deleted"]?.jsonPrimitive?.booleanOrNull?.let { span.setAttribute("tracy.conversation.deleted", it) }
            }
            op == "conversations.items.delete" -> {
                body["id"]?.jsonPrimitive?.contentOrNull?.let { span.setAttribute("tracy.conversation.item.id", it) }
                // conversation id may come from a nested "conversation" field or the context
                body["conversation_id"]?.jsonPrimitive?.contentOrNull?.let { span.setAttribute("gen_ai.conversation.id", it) }
                body["created_at"]?.jsonPrimitive?.longOrNull?.let { span.setAttribute("tracy.conversation.created_at", it) }
            }
            op == "conversations.items.list" -> {
                body["data"]?.let { data ->
                    if (data is JsonArray) {
                        span.setAttribute("tracy.conversation.items.count", data.size.toLong())
                    }
                }
                body["first_id"]?.jsonPrimitive?.contentOrNull?.let { span.setAttribute("tracy.conversation.items.first_id", it) }
                body["last_id"]?.jsonPrimitive?.contentOrNull?.let { span.setAttribute("tracy.conversation.items.last_id", it) }
                body["has_more"]?.jsonPrimitive?.booleanOrNull?.let { span.setAttribute("tracy.conversation.items.has_more", it) }
                // conversation_id may come from query param context
                response.url.pathSegments.let { segments ->
                    val convIndex = segments.indexOf("conversations")
                    if (convIndex != -1 && segments.size > convIndex + 1) {
                        span.setAttribute("gen_ai.conversation.id", segments[convIndex + 1])
                    }
                }
            }
            op == "conversations.items.create" -> {
                body["data"]?.let { data ->
                    if (data is JsonArray) {
                        span.setAttribute("tracy.conversation.items.count", data.size.toLong())
                    }
                }
                body["first_id"]?.jsonPrimitive?.contentOrNull?.let { span.setAttribute("tracy.conversation.items.first_id", it) }
                body["last_id"]?.jsonPrimitive?.contentOrNull?.let { span.setAttribute("tracy.conversation.items.last_id", it) }
                body["conversation_id"]?.jsonPrimitive?.contentOrNull?.let { span.setAttribute("gen_ai.conversation.id", it) }
                    ?: response.url.pathSegments.let { segments ->
                        val convIndex = segments.indexOf("conversations")
                        if (convIndex != -1 && segments.size > convIndex + 1) {
                            span.setAttribute("gen_ai.conversation.id", segments[convIndex + 1])
                        }
                    }
            }
            op == "conversations.items.retrieve" -> {
                body["id"]?.jsonPrimitive?.contentOrNull?.let { span.setAttribute("tracy.conversation.item.id", it) }
                body["type"]?.jsonPrimitive?.contentOrNull?.let { span.setAttribute("tracy.conversation.item.type", it) }
                body["status"]?.jsonPrimitive?.contentOrNull?.let { span.setAttribute("tracy.conversation.item.status", it) }
                body["conversation_id"]?.jsonPrimitive?.contentOrNull?.let { span.setAttribute("gen_ai.conversation.id", it) }
                    ?: response.url.pathSegments.let { segments ->
                        val convIndex = segments.indexOf("conversations")
                        if (convIndex != -1 && segments.size > convIndex + 1) {
                            span.setAttribute("gen_ai.conversation.id", segments[convIndex + 1])
                        }
                    }
            }
            else -> {
                // create, retrieve, update: conversation object
                body["id"]?.jsonPrimitive?.contentOrNull?.let { span.setAttribute("gen_ai.conversation.id", it) }
                body["created_at"]?.jsonPrimitive?.longOrNull?.let { span.setAttribute("tracy.conversation.created_at", it) }
            }
        }

        span.populateUnmappedAttributes(body, mappedResponseAttributes, PayloadType.RESPONSE)
    }

    override fun handleStreaming(span: Span, events: String) {
        // Conversations API does not support streaming
    }

    private fun detectConversationsOperation(url: TracyHttpUrl, method: String): String {
        val segments = url.pathSegments
        val convIndex = segments.indexOf("conversations")
        if (convIndex == -1) return "conversations.create"
        val afterConv = segments.drop(convIndex + 1).filter { it.isNotBlank() }
        return when {
            afterConv.isEmpty() && method == "POST" -> "conversations.create"
            afterConv.size == 1 && !afterConv.first().equals("items", ignoreCase = true) && method == "GET" -> "conversations.retrieve"
            afterConv.size == 1 && !afterConv.first().equals("items", ignoreCase = true) && method == "POST" -> "conversations.update"
            afterConv.size == 1 && !afterConv.first().equals("items", ignoreCase = true) && method == "DELETE" -> "conversations.delete"
            afterConv.contains("items") && afterConv.last().equals("items", ignoreCase = true) && method == "GET" -> "conversations.items.list"
            afterConv.contains("items") && afterConv.last().equals("items", ignoreCase = true) && method == "POST" -> "conversations.items.create"
            afterConv.contains("items") && !afterConv.last().equals("items", ignoreCase = true) && method == "GET" -> "conversations.items.retrieve"
            afterConv.contains("items") && !afterConv.last().equals("items", ignoreCase = true) && method == "DELETE" -> "conversations.items.delete"
            else -> "conversations.create"
        }
    }

    private val mappedResponseAttributes = listOf("id", "created_at", "deleted", "data", "first_id", "last_id", "has_more", "type", "status", "conversation_id")
}
