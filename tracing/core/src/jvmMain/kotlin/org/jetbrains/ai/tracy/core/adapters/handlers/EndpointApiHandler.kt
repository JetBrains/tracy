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
    fun handleStreamingEvent(span: Span, event: SseEvent, index: Long): Result<Boolean>
}

class SseEventHandlingException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

fun streamingFailure(message: String): Result<Boolean> {
    return Result.failure(SseEventHandlingException(message))
}
