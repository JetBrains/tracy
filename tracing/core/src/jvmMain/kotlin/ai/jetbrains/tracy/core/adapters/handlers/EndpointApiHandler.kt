package ai.jetbrains.tracy.core.adapters.handlers

import ai.jetbrains.tracy.core.http.protocol.Request
import ai.jetbrains.tracy.core.http.protocol.Response
import io.opentelemetry.api.trace.Span

/**
 * Interface for endpoint API handlers used within adapters
 */
interface EndpointApiHandler {
    fun handleRequestAttributes(span: Span, request: Request)
    fun handleResponseAttributes(span: Span, response: Response)
    fun handleStreaming(span: Span, events: String)
}
