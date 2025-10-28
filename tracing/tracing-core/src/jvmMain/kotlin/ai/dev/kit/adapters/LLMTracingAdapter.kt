package ai.dev.kit.adapters

import ai.dev.kit.exporters.BaseExporterConfig.Companion.DEFAULT_NUMBER_OF_SPAN_ATTRIBUTES
import ai.dev.kit.http.protocol.*
import ai.dev.kit.tracing.TracingManager
import io.ktor.http.*
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_SYSTEM
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive


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
        private val REQUIRED_CONTENT_TYPE = ContentType.Application.Json
        private val EVENT_STREAM_CONTENT_TYPE = ContentType.Text.EventStream

        private const val ATTRIBUTES_NUMBER_ATTRIBUTE_KEY = "gen_ai.request.SpanAttributeNumber"
    }

    fun registerRequest(span: Span, request: Request): Unit = runCatching {
        span.setAttribute(ATTRIBUTES_NUMBER_ATTRIBUTE_KEY, "0 (limit ${getMaxNumberOfSpanAttributes()})")
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
                if (response.contentType.match(REQUIRED_CONTENT_TYPE)) {
                    getResultBodyAttributes(span, response)
                } else if (isStreamingRequest && response.contentType.match(EVENT_STREAM_CONTENT_TYPE)) {
                    span.setAttribute("gen_ai.response.streaming", true)
                    span.setAttribute("gen_ai.completion.content.type", response.contentType.toString())
                } else {
                    span.setAttribute("gen_ai.completion.content.type", response.contentType.toString())
                }
            }

            span.setAttribute("http.status_code", response.code.toLong())

            if (response.isError()) {
                getResultErrorBodyAttributes(span, response.body)
                span.setStatus(StatusCode.ERROR)
            } else {
                span.setStatus(StatusCode.OK)
            }

            val numberOfSpanAttributes = (span as? ReadableSpan)?.attributes?.size()

            numberOfSpanAttributes?.let {
                val limit = getMaxNumberOfSpanAttributes()
                if (it >= limit) {
                    span.setAttribute(
                        ATTRIBUTES_NUMBER_ATTRIBUTE_KEY, "Limit ($limit) exceeded. Adjust TracingConfig or env"
                    )
                } else {
                    span.setAttribute(
                        ATTRIBUTES_NUMBER_ATTRIBUTE_KEY, "$it (limit $limit)"
                    )
                }
            }
            return@runCatching
        }.getOrElse { exception ->
            span.setStatus(StatusCode.ERROR)
            span.recordException(exception)
        }

    private fun getMaxNumberOfSpanAttributes() =
        TracingManager.openTelemetrySdk?.sdkTracerProvider?.spanLimits?.maxNumberOfAttributes
            ?: DEFAULT_NUMBER_OF_SPAN_ATTRIBUTES


    protected open fun getResultErrorBodyAttributes(span: Span, body: ResponseBody) {
        body.asJson()?.jsonObject["error"]?.jsonObject?.let { error ->
            error["message"]?.jsonPrimitive?.let { span.setAttribute("gen_ai.error.message", it.content) }
            error["type"]?.jsonPrimitive?.let { span.setAttribute("gen_ai.error.type", it.content) }
            error["param"]?.jsonPrimitive?.let { span.setAttribute("gen_ai.error.param", it.content) }
            error["code"]?.jsonPrimitive?.let { span.setAttribute("gen_ai.error.code", it.content) }
        }
    }

    protected abstract fun getRequestBodyAttributes(span: Span, request: Request)
    protected abstract fun getResultBodyAttributes(span: Span, response: Response)

    abstract fun isStreamingRequest(request: Request): Boolean
    abstract fun handleStreaming(span: Span, events: String)
}
