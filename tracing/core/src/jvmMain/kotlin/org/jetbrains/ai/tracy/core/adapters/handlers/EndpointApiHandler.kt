/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.core.adapters.handlers

import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpRequest
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpResponse
import io.opentelemetry.api.trace.Span
import org.jetbrains.ai.tracy.core.http.parsers.SseEvent

/**
 * Interface for endpoint API handlers used within adapters
 */
interface EndpointApiHandler {
    fun handleRequestAttributes(span: Span, request: TracyHttpRequest)
    fun handleResponseAttributes(span: Span, response: TracyHttpResponse)
    /**
     * Returns success if the event was handled successfully, or an error if it could not be handled.
     *
     * @see org.jetbrains.ai.tracy.core.adapters.handlers.sse.sseHandlingFailure
     * @see org.jetbrains.ai.tracy.core.adapters.handlers.sse.SseEventHandlingException
     */
    fun handleStreamingEvent(span: Span, event: SseEvent, index: Long): Result<Unit>
}
