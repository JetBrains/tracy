package ai.dev.kit.adapters.handlers

import ai.dev.kit.http.protocol.Request
import ai.dev.kit.http.protocol.Response
import io.opentelemetry.api.trace.Span

/**
 * Base interface for Gemini API handlers
 */
internal interface GeminiApiHandler : EndpointApiHandler
