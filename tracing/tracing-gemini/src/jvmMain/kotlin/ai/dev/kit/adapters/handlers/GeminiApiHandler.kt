package ai.dev.kit.adapters.handlers

import ai.dev.kit.http.protocol.Request
import ai.dev.kit.http.protocol.Response
import io.opentelemetry.api.trace.Span

/**
 * Base interface for Gemini API handlers
 */
internal interface GeminiApiHandler {
    fun handleRequestAttributes(span: Span, request: Request)
    fun handleResponseAttributes(span: Span, response: Response)
    fun handleStreaming(span: Span, events: String)
}