package ai.jetbrains.tracy.tracing.adapters.handlers

import ai.dev.kit.http.protocol.Request
import ai.dev.kit.http.protocol.Response
import ai.dev.kit.http.protocol.asJson
import io.opentelemetry.api.trace.Span
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.*
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Common utilities for OpenAI API handling
 */
internal object OpenAIApiUtils {
    
    /**
     * Sets common request attributes (temperature, model)
     */
    fun setCommonRequestAttributes(span: Span, request: Request) {
        val body = request.body.asJson()?.jsonObject ?: return

        body["temperature"]?.let { span.setAttribute(GEN_AI_REQUEST_TEMPERATURE, it.jsonPrimitive.doubleOrNull) }
        body["model"]?.let { span.setAttribute(GEN_AI_REQUEST_MODEL, it.jsonPrimitive.content) }
    }
    
    /**
     * Sets common response attributes (id, model, object type)
     */
    fun setCommonResponseAttributes(span: Span, response: Response) {
        val body = response.body.asJson()?.jsonObject ?: return

        body["id"]?.let { span.setAttribute(GEN_AI_RESPONSE_ID, it.jsonPrimitive.content) }
        body["object"]?.let { span.setAttribute(GEN_AI_OPERATION_NAME, it.jsonPrimitive.content) }
        body["model"]?.let { span.setAttribute(GEN_AI_RESPONSE_MODEL, it.jsonPrimitive.content) }
    }
}

internal val JsonElement.asString: String
    get() = when (this) {
        is JsonArray -> this.jsonArray.toString()
        is JsonObject -> this.jsonObject.toString()
        is JsonPrimitive -> this.jsonPrimitive.content
    }