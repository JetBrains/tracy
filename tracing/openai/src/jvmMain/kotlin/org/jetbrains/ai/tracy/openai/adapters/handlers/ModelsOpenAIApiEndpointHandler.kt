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
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_MODEL
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Handler for OpenAI Models API.
 *
 * See: [Models API](https://platform.openai.com/docs/api-reference/models)
 */
internal class ModelsOpenAIApiEndpointHandler : EndpointApiHandler {
    override fun handleRequestAttributes(span: Span, request: TracyHttpRequest) {
        val op = detectModelsOperation(request.url, request.method)
        span.setAttribute(GEN_AI_OPERATION_NAME, op)
        span.setAttribute("openai.api.type", "models")

        // For retrieve and delete, the model ID is in the URL path
        if (op == "models.retrieve" || op == "models.delete") {
            extractModelIdFromUrl(request.url)?.let {
                span.setAttribute(GEN_AI_REQUEST_MODEL, it)
            }
        }
    }

    override fun handleResponseAttributes(span: Span, response: TracyHttpResponse) {
        val body = response.body.asJson()?.jsonObject ?: return
        val op = detectModelsOperation(response.url, response.requestMethod)

        when (op) {
            "models.list" -> {
                body["data"]?.let { data ->
                    if (data is JsonArray) {
                        span.setAttribute("tracy.response.list.count", data.size.toLong())
                    }
                }
            }
            "models.retrieve" -> {
                body["id"]?.jsonPrimitive?.contentOrNull?.let { span.setAttribute("tracy.response.model.id", it) }
            }
            "models.delete" -> {
                body["id"]?.jsonPrimitive?.contentOrNull?.let { span.setAttribute("tracy.response.model.id", it) }
                body["deleted"]?.jsonPrimitive?.contentOrNull?.let { span.setAttribute("tracy.response.deleted", it.toBooleanStrictOrNull() ?: false) }
            }
        }

        // Capture error body if present
        body["error"]?.jsonObject?.let { error ->
            error["message"]?.jsonPrimitive?.contentOrNull?.let { span.setAttribute("tracy.response.error.message", it) }
            error["type"]?.jsonPrimitive?.contentOrNull?.let { span.setAttribute("tracy.response.error.type", it) }
            error["code"]?.jsonPrimitive?.contentOrNull?.let { span.setAttribute("tracy.response.error.code", it) }
        }

        span.populateUnmappedAttributes(body, mappedResponseAttributes, PayloadType.RESPONSE)
    }

    override fun handleStreaming(span: Span, events: String) {
        // Models API does not support streaming
    }

    private fun detectModelsOperation(url: TracyHttpUrl, method: String): String {
        val segments = url.pathSegments
        val modelsIndex = segments.indexOf("models")
        if (modelsIndex == -1) return "models.list"
        val afterModels = segments.drop(modelsIndex + 1).filter { it.isNotBlank() }
        return when {
            afterModels.isEmpty() && method == "GET" -> "models.list"
            afterModels.size == 1 && method == "GET" -> "models.retrieve"
            afterModels.size == 1 && method == "DELETE" -> "models.delete"
            else -> "models.list"
        }
    }

    private fun extractModelIdFromUrl(url: TracyHttpUrl): String? {
        val segments = url.pathSegments
        val modelsIndex = segments.indexOf("models")
        if (modelsIndex == -1) return null
        val afterModels = segments.drop(modelsIndex + 1).filter { it.isNotBlank() }
        return afterModels.firstOrNull()
    }

    private val mappedResponseAttributes = listOf("id", "data", "deleted", "error")
}
