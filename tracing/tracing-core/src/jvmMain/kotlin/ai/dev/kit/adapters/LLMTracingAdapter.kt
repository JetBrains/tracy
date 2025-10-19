package ai.dev.kit.adapters

import ai.dev.kit.tracing.TracingManager
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_SYSTEM
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive


data class Url(
    val scheme: String,
    val host: String,
    val pathSegments: List<String>,
)

data class ContentType(val type: String, val subtype: String) {
    fun asString(): String = "$type/$subtype"
}

/**
 * A sealed class representing an adapter for tracing LLM interactions.
 * This adapter handles the tracing of HTTP requests and responses, extracting relevant attributes
 * from both the request and response bodies to populate spans.
 *
 * @constructor Initializes the adapter with the name of the GenAI system being traced.
 * @param genAISystem The name of the Generative AI system that this adapter represents.
 */
abstract class LLMTracingAdapter(private val genAISystem: String) {
    companion object {
        private const val REQUIRED_TYPE = "application"
        private const val REQUIRED_SUBTYPE = "json"
    }

    fun registerRequest(span: Span, url: Url, requestBody: JsonObject) {
        span.setAttribute("gen_ai.request.SpanAttributeNumber", "0 (limit ${TracingManager.maxNumberOfSpanAttributes})")

        getRequestBodyAttributes(span, url, requestBody)
        span.setAttribute("gen_ai.api_base", "${url.scheme}://${url.host}")
        span.setAttribute(GEN_AI_SYSTEM, genAISystem)
    }

    fun registerResponse(span: Span, contentType: ContentType?, responseCode: Long, responseBody: JsonObject) {
        val isStreamingRequest = responseBody["stream"]?.jsonPrimitive?.boolean == true

        if (contentType?.type == REQUIRED_TYPE && contentType.subtype == REQUIRED_SUBTYPE) {
            getResultBodyAttributes(span, responseBody)
        } else if (isStreamingRequest && contentType.toString().contains("text/event-stream")) {
            span.setAttribute("gen_ai.response.streaming", true)
            span.setAttribute("gen_ai.completion.content.type", contentType.toString())
        } else {
            contentType?.let { span.setAttribute("gen_ai.completion.content.type", it.asString()) }
        }

        span.setAttribute("http.status_code", responseCode)
        if (responseCode in 400..499 || responseCode in 500..599) {
            getResultErrorBodyAttributes(span, responseBody)
            span.setStatus(StatusCode.ERROR)
        } else {
            span.setStatus(StatusCode.OK)
        }

        val numberOfSpanAttributes = (span as? ReadableSpan)?.attributes?.size()

        numberOfSpanAttributes?.let {
            val limit = TracingManager.maxNumberOfSpanAttributes ?: Int.MAX_VALUE
            if (it >= limit) {
                span.setAttribute(
                    "gen_ai.request.SpanAttributeNumber",
                    "Limit (${TracingManager.maxNumberOfSpanAttributes}) exceeded. Adjust TracingConfig or env"
                )
            } else {
                span.setAttribute(
                    "gen_ai.request.SpanAttributeNumber",
                    "$it (limit ${TracingManager.maxNumberOfSpanAttributes})"
                )
            }
        }
    }

    protected open fun getResultErrorBodyAttributes(span: Span, body: JsonObject) {
        body["error"]?.jsonObject?.let { error ->
            error["message"]?.jsonPrimitive?.let { span.setAttribute("gen_ai.error.message", it.content) }
            error["type"]?.jsonPrimitive?.let { span.setAttribute("gen_ai.error.type", it.content) }
            error["param"]?.jsonPrimitive?.let { span.setAttribute("gen_ai.error.param", it.content) }
            error["code"]?.jsonPrimitive?.let { span.setAttribute("gen_ai.error.code", it.content) }
        }
    }

    protected abstract fun getRequestBodyAttributes(span: Span, url: Url, body: JsonObject)

    protected abstract fun getResultBodyAttributes(span: Span, body: JsonObject)

    abstract fun isStreamingRequest(body: JsonObject?): Boolean
    abstract fun handleStreaming(span: Span, events: String)
}