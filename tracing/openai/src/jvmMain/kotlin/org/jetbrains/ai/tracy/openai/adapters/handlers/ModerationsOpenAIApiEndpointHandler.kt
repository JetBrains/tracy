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
import org.jetbrains.ai.tracy.core.http.protocol.asJson
import io.opentelemetry.api.trace.Span
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_OPERATION_NAME
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Handler for OpenAI Moderations API.
 *
 * See: [Moderations API](https://platform.openai.com/docs/api-reference/moderations)
 */
internal class ModerationsOpenAIApiEndpointHandler : EndpointApiHandler {
    override fun handleRequestAttributes(span: Span, request: TracyHttpRequest) {
        span.setAttribute(GEN_AI_OPERATION_NAME, "moderations")
        span.setAttribute("openai.api.type", "moderations")
        OpenAIApiUtils.setCommonRequestAttributes(span, request)

        val body = request.body.asJson()?.jsonObject ?: return

        // Determine input type: string, string_array, or multimodal
        body["input"]?.let { input ->
            val inputType = when (input) {
                is JsonPrimitive -> "string"
                is JsonArray -> {
                    val hasObjectItems = input.jsonArray.any { it is JsonObject }
                    if (hasObjectItems) "multimodal" else "string_array"
                }
                else -> "string"
            }
            span.setAttribute("tracy.request.input.type", inputType)
        }

        span.populateUnmappedAttributes(body, mappedRequestAttributes, PayloadType.REQUEST)
    }

    override fun handleResponseAttributes(span: Span, response: TracyHttpResponse) {
        val body = response.body.asJson()?.jsonObject ?: return
        OpenAIApiUtils.setCommonResponseAttributes(span, response)

        body["results"]?.let { results ->
            if (results is JsonArray) {
                span.setAttribute("tracy.response.results.count", results.size.toLong())
                if (results.isNotEmpty()) {
                    val firstResult = results.jsonArray.first().jsonObject
                    firstResult["flagged"]?.jsonPrimitive?.let {
                        span.setAttribute("tracy.response.results.flagged", it.toString())
                    }
                    firstResult["categories"]?.let {
                        span.setAttribute("tracy.response.results.categories", it.toString())
                    }
                    firstResult["category_scores"]?.let {
                        span.setAttribute("tracy.response.results.category_scores", it.toString())
                    }
                    firstResult["category_applied_input_types"]?.let {
                        span.setAttribute("tracy.response.results.category_applied_input_types", it.toString())
                    }
                }
            }
        }

        span.populateUnmappedAttributes(body, mappedResponseAttributes, PayloadType.RESPONSE)
    }

    override fun handleStreaming(span: Span, events: String) {
        // Moderations API does not support streaming
    }

    private val mappedRequestAttributes = listOf("model", "input")
    private val mappedResponseAttributes = listOf("results", "id", "model")
}
