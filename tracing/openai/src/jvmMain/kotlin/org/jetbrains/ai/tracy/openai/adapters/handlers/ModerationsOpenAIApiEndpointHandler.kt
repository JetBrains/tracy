/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.openai.adapters.handlers

import org.jetbrains.ai.tracy.core.adapters.handlers.EndpointApiHandler
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpRequest
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpResponse
import org.jetbrains.ai.tracy.core.http.protocol.asJson
import org.jetbrains.ai.tracy.core.policy.orRedactedInput
import io.opentelemetry.api.trace.Span
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_OPERATION_NAME
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_MODEL
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Handler for OpenAI Moderations API.
 *
 * See: [Moderations API Reference](https://platform.openai.com/docs/api-reference/moderations)
 */
internal class ModerationsOpenAIApiEndpointHandler : EndpointApiHandler {
    override fun handleRequestAttributes(span: Span, request: TracyHttpRequest) {
        span.setAttribute(GEN_AI_OPERATION_NAME, "moderations")

        val body = request.body.asJson()?.jsonObject ?: return

        body["model"]?.let { span.setAttribute(GEN_AI_REQUEST_MODEL, it.jsonPrimitive.content) }
        body["input"]?.let { span.setAttribute("gen_ai.request.input", it.asString.orRedactedInput()) }
    }

    override fun handleResponseAttributes(span: Span, response: TracyHttpResponse) {
        val body = response.body.asJson()?.jsonObject ?: return

        body["results"]?.jsonArray?.firstOrNull()?.jsonObject?.let { result ->
            result["flagged"]?.jsonPrimitive?.boolean?.let { flagged ->
                span.setAttribute("gen_ai.moderation.flagged", flagged)
            }
        }
    }

    override fun handleStreaming(span: Span, events: String) {
        // Moderations API does not support streaming
    }
}
