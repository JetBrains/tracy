/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package ai.jetbrains.tracy.core.adapters.handlers

import ai.jetbrains.tracy.core.http.protocol.TracyHttpRequest
import ai.jetbrains.tracy.core.http.protocol.TracyHttpResponse
import io.opentelemetry.api.trace.Span

/**
 * Interface for endpoint API handlers used within adapters
 */
interface EndpointApiHandler {
    fun handleRequestAttributes(span: Span, request: TracyHttpRequest)
    fun handleResponseAttributes(span: Span, response: TracyHttpResponse)
    fun handleStreaming(span: Span, events: String)
}
