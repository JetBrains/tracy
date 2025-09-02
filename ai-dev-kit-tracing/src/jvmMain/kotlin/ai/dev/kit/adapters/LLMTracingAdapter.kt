package ai.dev.kit.adapters

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_SYSTEM
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive


data class Url(val scheme: String, val host: String)

data class ContentType(val type: String, val subtype: String) {
    fun asString(): String = "$type/$subtype"
}

internal sealed class LLMTracingAdapter(private val genAISystem: String) {
    companion object {
        private const val REQUIRED_TYPE = "application"
        private const val REQUIRED_SUBTYPE = "json"
    }

    fun registerRequest(span: Span, url: Url, requestBody: JsonObject) {
        getRequestBodyAttributes(span, url, requestBody)
        span.setAttribute("gen_ai.api_base", "${url.scheme}://${url.host}")
        // TODO: get from parameters
        span.setAttribute(GEN_AI_SYSTEM, genAISystem)
    }

    fun registerResponse(span: Span, contentType: ContentType?, responseCode: Long, responseBody: JsonObject) {
        if (contentType?.type == REQUIRED_TYPE && contentType.subtype == REQUIRED_SUBTYPE) {
            getResultBodyAttributes(span, responseBody)
        } else {
            contentType?.let { span.setAttribute("gen_ai.completion.content.type", it.asString()) }
        }

        span.setAttribute("http.status_code", responseCode)
        if (responseCode in 400..499 || responseCode in 500..599) {
            getResultErrorBodyAttributes(span, responseBody)
            span.setStatus(StatusCode.ERROR)
        }
        else {
            span.setStatus(StatusCode.OK)
        }
    }

    protected open fun getResultErrorBodyAttributes(span: Span, body: JsonObject) {
        body["error"]?.jsonObject?.let {
            it["message"]?.jsonPrimitive?.let { span.setAttribute("gen_ai.error.message", it.content) }
            it["type"]?.jsonPrimitive?.let { span.setAttribute("gen_ai.error.type", it.content) }
            it["param"]?.jsonPrimitive?.let { span.setAttribute("gen_ai.error.param", it.content) }
            it["code"]?.jsonPrimitive?.let { span.setAttribute("gen_ai.error.code", it.content) }
        }
    }

    protected abstract fun getRequestBodyAttributes(span: Span, url: Url, body: JsonObject)

    protected abstract fun getResultBodyAttributes(span: Span, body: JsonObject)
}