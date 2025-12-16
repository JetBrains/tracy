package ai.dev.kit.adapters.handlers

import ai.dev.kit.http.protocol.Request
import ai.dev.kit.http.protocol.Response
import io.opentelemetry.api.trace.Span

/**
 * Interface for endpoint API handlers used within adapters
 */
interface EndpointApiHandler {
    fun handleRequestAttributes(span: Span, request: Request)
    fun handleResponseAttributes(span: Span, response: Response)
    fun handleStreaming(span: Span, events: String)
}