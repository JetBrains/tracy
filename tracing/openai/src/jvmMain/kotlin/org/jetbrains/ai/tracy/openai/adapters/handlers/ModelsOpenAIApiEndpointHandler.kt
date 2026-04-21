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
 * Handler for OpenAI Models API.
 *
 * Since GET /v1/models/{id} carries no request body, the model ID is extracted
 * from the URL path's last segment.
 *
 * See: [Models API Reference](https://platform.openai.com/docs/api-reference/models)
 */
internal class ModelsOpenAIApiEndpointHandler : EndpointApiHandler {
    override fun handleRequestAttributes(span: Span, request: TracyHttpRequest) {
        // GET /v1/models/{id} — model ID is the last path segment
        val lastSegment = request.url.pathSegments.lastOrNull { it.isNotBlank() }
        if (lastSegment == null || lastSegment == "models") {
            span.setAttribute(GEN_AI_OPERATION_NAME, "models.list")
        } else {
            span.setAttribute(GEN_AI_OPERATION_NAME, "models.retrieve")
            span.setAttribute(GEN_AI_REQUEST_MODEL, lastSegment)
        }
    }

    override fun handleResponseAttributes(span: Span, response: TracyHttpResponse) {
        // No additional attributes to extract beyond what setCommonResponseAttributes provides
    }

    override fun handleStreaming(span: Span, events: String) {
        // Models API does not support streaming
    }
}
