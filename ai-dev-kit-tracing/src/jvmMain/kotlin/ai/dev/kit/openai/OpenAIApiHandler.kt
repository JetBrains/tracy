package ai.dev.kit.openai

import io.opentelemetry.api.trace.Span
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl

/**
 * Base interface for OpenAI API handlers
 */
internal interface OpenAIApiHandler {
    fun handleRequestAttributes(span: Span, url: HttpUrl, body: JsonObject)
    fun handleResponseAttributes(span: Span, body: JsonObject)
}

/**
 * Common utilities for OpenAI API handling
 */
internal object OpenAIApiUtils {
    
    /**
     * Sets common request attributes (temperature, model, API base)
     */
    fun setCommonRequestAttributes(span: Span, url: HttpUrl, body: JsonObject) {
        body["temperature"]?.let { span.setAttribute(GEN_AI_REQUEST_TEMPERATURE, it.jsonPrimitive.content.toDouble()) }
        body["model"]?.let { span.setAttribute(GEN_AI_REQUEST_MODEL, it.jsonPrimitive.content) }
        span.setAttribute("gen_ai.api_base", "${url.scheme}://${url.host}")
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
