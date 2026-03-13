/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.core.adapters.handlers

import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpRequest
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpResponse
import io.opentelemetry.api.trace.Span

/**
 * Interface for endpoint API handlers used within adapters.
 */
interface EndpointApiHandler {
    /**
     * Extracts request span attributes (model, messages, tools, etc.) from the request body.
     */
    fun handleRequestAttributes(span: Span, request: TracyHttpRequest)

    /**
     * Extracts response span attributes (completions, usage, tool calls, etc.) from a non-streaming response.
     */
    fun handleResponseAttributes(span: Span, response: TracyHttpResponse)

    /**
     * Extracts response span attributes from a streaming (SSE) response.
     * [events] is the full accumulated SSE text.
     */
    fun handleStreaming(span: Span, events: String)
}
