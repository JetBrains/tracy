package ai.dev.kit.adapters.openai

import ai.dev.kit.adapters.Url
import io.opentelemetry.api.trace.Span
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Base interface for OpenAI API handlers
 */
internal interface OpenAIApiHandler {
    fun handleRequestAttributes(span: Span, url: Url, body: JsonObject)
    fun handleResponseAttributes(span: Span, body: JsonObject)
}

/**
 * Common utilities for OpenAI API handling
 */
internal object OpenAIApiUtils {
    
    /**
     * Sets common request attributes (temperature, model, API base)
     */
    fun setCommonRequestAttributes(span: Span, url: Url, body: JsonObject) {
        body["temperature"]?.let { span.setAttribute(GEN_AI_REQUEST_TEMPERATURE, it.jsonPrimitive.doubleOrNull) }
        body["model"]?.let { span.setAttribute(GEN_AI_REQUEST_MODEL, it.jsonPrimitive.content) }
    }
    
    /**
     * Sets common response attributes (id, model, object type)
     */
    fun setCommonResponseAttributes(span: Span, body: JsonObject) {
        body["id"]?.let { span.setAttribute(GEN_AI_RESPONSE_ID, it.jsonPrimitive.content) }
        body["object"]?.let { span.setAttribute("llm.request.type", it.jsonPrimitive.content) }
        body["model"]?.let { span.setAttribute(GEN_AI_RESPONSE_MODEL, it.jsonPrimitive.content) }
    }
}
