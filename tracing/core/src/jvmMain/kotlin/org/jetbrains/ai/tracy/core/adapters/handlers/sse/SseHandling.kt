/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.core.adapters.handlers.sse

/**
 * Wraps [SseEventHandlingException] into a [Result] indicating failure.
 * Should be used when handling of SSE events failed.
 *
 * @see org.jetbrains.ai.tracy.core.adapters.handlers.EndpointApiHandler.handleStreamingEvent
 */
fun sseHandlingFailure(message: String): Result<Unit> {
    return Result.failure(SseEventHandlingException(message))
}

class SseEventHandlingException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
