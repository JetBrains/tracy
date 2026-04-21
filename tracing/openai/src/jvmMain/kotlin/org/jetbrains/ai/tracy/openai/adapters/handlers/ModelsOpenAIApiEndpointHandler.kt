/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.openai.adapters.handlers

import org.jetbrains.ai.tracy.core.adapters.handlers.EndpointApiHandler
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpRequest
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpResponse
import io.opentelemetry.api.trace.Span
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_OPERATION_NAME
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_MODEL

/**
 * Handler for OpenAI Models retrieve API.
 *
 * Handles requests to `/v1/models/{model_id}` (retrieve a specific model).
 * Note: The list endpoint `/v1/models` is handled by the default chat-completions
 * handler since the route pattern "models/" only matches paths with a trailing segment.
 *
 * See [Models API](https://platform.openai.com/docs/api-reference/models/retrieve)
 */
internal class ModelsOpenAIApiEndpointHandler : EndpointApiHandler {

    override fun handleRequestAttributes(span: Span, request: TracyHttpRequest) {
        // Extract the model ID from the last URL path segment.
        // E.g., /v1/models/gpt-4o-mini → "gpt-4o-mini"
        val modelId = request.url.pathSegments.lastOrNull() ?: return
        span.setAttribute(GEN_AI_REQUEST_MODEL, modelId)
    }

    override fun handleResponseAttributes(span: Span, response: TracyHttpResponse) {
        // The response body contains `"object": "model"`, but the OTel semantic operation
        // name for this endpoint is "models.retrieve". Override what setCommonResponseAttributes
        // wrote from the response "object" field.
        span.setAttribute(GEN_AI_OPERATION_NAME, "models.retrieve")
    }

    override fun handleStreaming(span: Span, events: String) {
        // Models API does not support streaming
    }
}
