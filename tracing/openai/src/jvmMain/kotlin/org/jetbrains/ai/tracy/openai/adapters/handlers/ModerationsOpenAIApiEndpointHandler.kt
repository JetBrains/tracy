/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.openai.adapters.handlers

import org.jetbrains.ai.tracy.core.adapters.handlers.EndpointApiHandler
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpRequest
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpResponse
import org.jetbrains.ai.tracy.core.http.protocol.asJson
import io.opentelemetry.api.trace.Span
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Handler for OpenAI Moderations API.
 *
 * See [Moderations API](https://platform.openai.com/docs/api-reference/moderations)
 */
internal class ModerationsOpenAIApiEndpointHandler : EndpointApiHandler {

    override fun handleRequestAttributes(span: Span, request: TracyHttpRequest) {
        val body = request.body.asJson()?.jsonObject ?: return

        body["input"]?.let { input ->
            val inputStr = when (input) {
                is JsonArray -> input.jsonArray
                    .mapNotNull { it.jsonPrimitive.contentOrNull }
                    .joinToString(" ")
                else -> input.jsonPrimitive.contentOrNull ?: return@let
            }
            span.setAttribute("gen_ai.request.input", inputStr)
        }
    }

    override fun handleResponseAttributes(span: Span, response: TracyHttpResponse) {
        val body = response.body.asJson()?.jsonObject ?: return

        body["results"]?.jsonArray?.firstOrNull()?.jsonObject?.let { result ->
            result["flagged"]?.jsonPrimitive?.booleanOrNull?.let { flagged ->
                span.setAttribute("gen_ai.moderation.flagged", flagged)
            }
        }
    }

    override fun handleStreaming(span: Span, events: String) {
        // Moderations API does not support streaming
    }
}
