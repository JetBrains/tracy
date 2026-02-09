package ai.jetbrains.tracy.core.adapters

import ai.jetbrains.tracy.core.http.protocol.Url
import ai.jetbrains.tracy.core.http.protocol.Request
import ai.jetbrains.tracy.core.http.protocol.Response
import ai.jetbrains.tracy.core.http.protocol.ResponseBody
import ai.jetbrains.tracy.core.http.protocol.asJson
import ai.jetbrains.tracy.core.http.protocol.isError
import io.ktor.http.ContentType
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_SYSTEM
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive


/**
 * An abstract class representing an adapter for tracing LLM interactions.
 * This adapter handles the tracing of HTTP requests and responses, extracting relevant attributes
 * from both the request and response bodies to populate spans.
 *
 * @constructor Initializes the adapter with the name of the GenAI system being traced.
 * @param genAISystem The name of the Generative AI system that this adapter represents.
 */
abstract class LLMTracingAdapter(private val genAISystem: String) {
    fun registerRequest(span: Span, request: Request): Unit = runCatching {
        span.updateName(getSpanName(request))

        // Pre-allocate in case the span reaches the limit
        span.setAttribute(DROPPED_ATTRIBUTES_COUNT_ATTRIBUTE_KEY, 0L)

        getRequestBodyAttributes(span, request)
        span.setAttribute("gen_ai.api_base", "${request.url.scheme}://${request.url.host}")
        span.setAttribute(GEN_AI_SYSTEM, genAISystem)

        return@runCatching
    }.getOrElse { exception ->
        span.setStatus(StatusCode.ERROR)
        span.recordException(exception)
    }

    fun registerResponse(span: Span, response: Response): Unit =
        runCatching {
            val body = response.body.asJson()?.jsonObject ?: return
            val isStreamingRequest = body["stream"]?.jsonPrimitive?.boolean == true

            if (response.contentType != null) {
                when {
                    response.contentType.match(REQUIRED_CONTENT_TYPE) -> {
                        getResponseBodyAttributes(span, response)
                    }
                    isStreamingRequest && response.contentType.match(EVENT_STREAM_CONTENT_TYPE) -> {
                        span.setAttribute("gen_ai.response.streaming", true)
                        span.setAttribute("gen_ai.completion.content.type", response.contentType.toString())
                    }
                    else -> {
                        span.setAttribute("gen_ai.completion.content.type", response.contentType.toString())
                    }
                }
            }

            span.setAttribute("http.status_code", response.code.toLong())

            if (response.isError()) {
                getResponseErrorBodyAttributes(span, response.body)
                span.setStatus(StatusCode.ERROR)
            } else {
                span.setStatus(StatusCode.OK)
            }

            val spanData = (span as? ReadableSpan)?.toSpanData() ?: return@runCatching
            val totalAttributesCount = spanData.totalAttributeCount
            val keptAttributesCount = spanData.attributes.size()

            // Calculate the number of attributes that were dropped due to span limits
            val droppedAttributesCount = (totalAttributesCount - keptAttributesCount).coerceAtLeast(0).toLong()

            span.setAttribute(DROPPED_ATTRIBUTES_COUNT_ATTRIBUTE_KEY, droppedAttributesCount)
        }.getOrElse { exception ->
            span.setStatus(StatusCode.ERROR)
            span.recordException(exception)
        }

    protected open fun getResponseErrorBodyAttributes(span: Span, body: ResponseBody) {
        body.asJson()?.jsonObject["error"]?.jsonObject?.let { error ->
            error["message"]?.jsonPrimitive?.let { span.setAttribute("gen_ai.error.message", it.content) }
            error["type"]?.jsonPrimitive?.let { span.setAttribute("gen_ai.error.type", it.content) }
            error["param"]?.jsonPrimitive?.let { span.setAttribute("gen_ai.error.param", it.content) }
            error["code"]?.jsonPrimitive?.let { span.setAttribute("gen_ai.error.code", it.content) }
        }
    }

    protected abstract fun getRequestBodyAttributes(span: Span, request: Request)
    protected abstract fun getResponseBodyAttributes(span: Span, response: Response)

    abstract fun getSpanName(request: Request): String
    abstract fun isStreamingRequest(request: Request): Boolean
    abstract fun handleStreaming(span: Span, url: Url, events: String)

    companion object {
        private val REQUIRED_CONTENT_TYPE = ContentType.Application.Json
        private val EVENT_STREAM_CONTENT_TYPE = ContentType.Text.EventStream

        private const val DROPPED_ATTRIBUTES_COUNT_ATTRIBUTE_KEY = "otel.dropped_attributes_count"

        /**
         * Adds unmapped payload attributes from a JSON body to the given [Span].
         *
         * @param body the request or response body
         * @param mappedAttributes a list of attribute keys that have already been
         *  handled and should be skipped when populating unmapped attributes
         */
        fun Span.populateUnmappedAttributes(
            body: JsonObject,
            mappedAttributes: List<String>,
            payloadType: PayloadType
        ) {
            body.entries.forEach { (key, value) ->
                if (key !in (mappedAttributes)) {
                    val attributeKey = "tracy.${payloadType.value}.$key"
                    setAttribute(attributeKey, value.toString())
                }
            }
        }

        enum class PayloadType(val value: String) {
            REQUEST("request"),
            RESPONSE("response")
        }
    }
}
