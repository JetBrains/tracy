package ai.dev.kit

import ai.dev.kit.tracing.AI_DEVELOPMENT_KIT_TRACER
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_CHOICE_COUNT
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_MAX_TOKENS
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_MODEL
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_TEMPERATURE
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_TOP_K
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_TOP_P
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_RESPONSE_ID
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_RESPONSE_MODEL
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_SYSTEM
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_USAGE_INPUT_TOKENS
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_USAGE_OUTPUT_TOKENS
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GenAiSystemIncubatingValues
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import com.google.genai.Client as GeminiClient


fun instrument(client: GeminiClient): GeminiClient {
    return patchClient(client, interceptor = OpenTelemetryGeminiLogger())
}

private fun patchClient(client: GeminiClient, interceptor: Interceptor): GeminiClient {
    val apiClientField = GeminiClient::class.java.getDeclaredField("apiClient")
        .apply { isAccessible = true }
    val apiClient = apiClientField.get(client)

    val httpClientField = apiClient.javaClass.superclass.getDeclaredField("httpClient")
        .apply { isAccessible = true }
    val httpClient = httpClientField.get(apiClient)

    val interceptorsField = OkHttpClient::class.java.getDeclaredField("interceptors").apply { isAccessible = true }
    interceptorsField.set(httpClient, listOf(interceptor))

    return client
}

private const val SPAN_NAME = "Gemini-generation"

/**
 * For request and response schemas, see: [Gemini Docs](https://ai.google.dev/api/generate-content)
 */
class OpenTelemetryGeminiLogger : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val tracer = GlobalOpenTelemetry.getTracer(AI_DEVELOPMENT_KIT_TRACER)

        val span = tracer.spanBuilder(SPAN_NAME).startSpan()

        span.makeCurrent().use { scopeIgnored ->
            try {
                val request = chain.request()
                val url = request.url
                val body = request.body?.let {
                    val buffer = okio.Buffer()
                    it.writeTo(buffer)
                    Json.parseToJsonElement(buffer.readUtf8()).jsonObject
                }

                body?.let { getRequestBodyAttributes(span, url, it) }
                span.setAttribute("gen_ai.gemini.api_base", "${url.scheme}://${url.host}")

                // TODO: get from parameters
                span.setAttribute(GEN_AI_SYSTEM, GenAiSystemIncubatingValues.GEMINI)

                val response = chain.proceed(chain.request())

                val contentType = response.body?.contentType()
                val requiredMediaType = "application/json".toMediaType()

                if (contentType?.type == requiredMediaType.type &&
                    contentType.subtype == requiredMediaType.subtype) {
                    // We need to peek the body so the stream is not consumed
                    val decodedResponse = Json.decodeFromString<JsonObject>(response.peekBody(Long.MAX_VALUE).string())
                    getResultBodyAttributes(span, decodedResponse)
                } else {
                    contentType?.let { span.setAttribute("gen_ai.completion.content.type", it.toString()) }
                }

                span.setAttribute("http.status_code", response.code.toLong())
                // for any 4xx response code, treat a failure
                if (response.code in 400..499 || response.code in 500..599) {
                    val decodedResponse = Json.decodeFromString<JsonObject>(response.peekBody(Long.MAX_VALUE).string())
                    getResultErrorBodyAttributes(span, decodedResponse)
                    span.setStatus(StatusCode.ERROR)
                } else {
                    span.setStatus(StatusCode.OK)
                }

                return response
            } catch (e: Exception) {
                span.setStatus(StatusCode.ERROR)
                span.recordException(e)
                throw e
            } finally {
                span.end()
            }
        }
    }

    private fun getResultErrorBodyAttributes(span: Span, body: JsonObject) {
        body["error"]?.jsonObject?.let {
            it["message"]?.jsonPrimitive?.let { span.setAttribute("gen_ai.error.message", it.content) }
            it["type"]?.jsonPrimitive?.let { span.setAttribute("gen_ai.error.type", it.content) }
            it["param"]?.jsonPrimitive?.let { span.setAttribute("gen_ai.error.param", it.content) }
            it["code"]?.jsonPrimitive?.let { span.setAttribute("gen_ai.error.code", it.content) }
        }
    }

    private fun getRequestBodyAttributes(span: Span, url: HttpUrl, body: JsonObject) {
        // See: https://ai.google.dev/api/caching#Content
        body["contents"]?.let {
            for ((index, message) in it.jsonArray.withIndex()) {
                span.setAttribute("gen_ai.prompt.$index.role", message.jsonObject["role"]?.jsonPrimitive?.content)
                // prompt parts
                val content = message.jsonObject["parts"]?.jsonArray
                    ?.joinToString(" ") { it.jsonObject["text"]?.jsonPrimitive?.content.orEmpty() }
                    ?: ""
                span.setAttribute("gen_ai.prompt.$index.content", content)
            }
        }

        // url ends with `[model]:[operation]`
        val (model, operation) = url.pathSegments.lastOrNull()?.split(":")
            ?.let { it.firstOrNull() to it.lastOrNull() } ?: (null to null)

        model?.let { span.setAttribute(GEN_AI_REQUEST_MODEL, model) }
        // TODO: use GEN_AI_OPERATION_NAME?
        operation?.let { span.setAttribute("llm.request.type", operation) }

        // See: https://ai.google.dev/api/generate-content#v1beta.GenerationConfig
        body["generationConfig"]?.let {
            it.jsonObject["candidateCount"]?.jsonPrimitive?.int?.let {
                span.setAttribute(GEN_AI_REQUEST_CHOICE_COUNT, it.toLong())
            }
            it.jsonObject["maxOutputTokens"]?.jsonPrimitive?.int?.let {
                span.setAttribute(GEN_AI_REQUEST_MAX_TOKENS, it.toLong())
            }
            it.jsonObject["temperature"]?.jsonPrimitive?.double?.let { span.setAttribute(GEN_AI_REQUEST_TEMPERATURE, it) }
            it.jsonObject["topP"]?.jsonPrimitive?.double?.let { span.setAttribute(GEN_AI_REQUEST_TOP_P, it) }
            it.jsonObject["topK"]?.jsonPrimitive?.double?.let { span.setAttribute(GEN_AI_REQUEST_TOP_K, it) }
        }
    }

    private fun getResultBodyAttributes(span: Span, body: JsonObject) {
        // See: https://ai.google.dev/api/generate-content#v1beta.GenerateContentResponse
        body["responseId"]?.let { span.setAttribute(GEN_AI_RESPONSE_ID, it.jsonPrimitive.content) }
        body["modelVersion"]?.let { span.setAttribute(GEN_AI_RESPONSE_MODEL, it.jsonPrimitive.content) }

        body["candidates"]?.let {
            for ((index, candidate) in it.jsonArray.withIndex()) {
                candidate.jsonObject["content"]?.let { content ->
                    span.setAttribute(
                        "gen_ai.completion.$index.role",
                        content.jsonObject["role"]?.jsonPrimitive?.content
                    )
                    // response parts
                    val content = content.jsonObject["parts"]?.jsonArray
                        ?.joinToString(" ") { it.jsonObject["text"]?.jsonPrimitive?.content.orEmpty() }
                        ?: ""
                    span.setAttribute("gen_ai.completion.$index.content", content)

                    // TODO: add tool and function calling
                    /*
                    span.setAttribute("gen_ai.completion.$index.tool_calls", message.jsonObject["tool_calls"]?.jsonPrimitive?.content)
                    span.setAttribute("gen_ai.completion.$index.function_call", message.jsonObject["function_call"]?.jsonPrimitive?.content)
                    span.setAttribute("gen_ai.completion.$index.annotations", message.jsonObject["annotations"].toString())
                     */
                }

                span.setAttribute(
                    "gen_ai.completion.$index.finish_reason",
                    candidate.jsonObject["finishReason"]?.jsonPrimitive?.content
                )
            }
        }

        body["usageMetadata"]?.let { usage ->
            usage.jsonObject["promptTokenCount"]?.jsonPrimitive?.int?.let {
                span.setAttribute(GEN_AI_USAGE_INPUT_TOKENS, it)
            }
            usage.jsonObject["candidatesTokenCount"]?.jsonPrimitive?.int?.let {
                span.setAttribute(GEN_AI_USAGE_OUTPUT_TOKENS, it)
            }
            usage.jsonObject["totalTokenCount"]?.jsonPrimitive?.int?.let {
                span.setAttribute("gen_ai.usage.total_tokens", it.toLong())
            }

            // TODO: think about the mapping of the below properties (see: https://github.com/JetBrains/ai-dev-kit/pull/54#discussion_r2229750741)
            /**
             * The following two properties (`promptTokensDetails`, `candidatesTokensDetails`)
             * and their inner contents are mapped into snake-cased OTEL attributes.
             *
             * 1. For `promptTokensDetails`:
             *   - `"gen_ai.usage.prompt_tokens_details.0.modality"`
             *   - `"gen_ai.usage.prompt_tokens_details.0.token_count"`
             * 2. For `candidatesTokensDetails`:
             *   - `"gen_ai.usage.candidates_tokens_details.0.modality"`
             *   - `"gen_ai.usage.candidates_tokens_details.0.token_count"`
             *
             * See: https://ai.google.dev/api/generate-content#UsageMetadata
             */
            // prompt tokens details
            extractUsageTokenDetails(span, usage, attribute = "promptTokensDetails")
            // candidate tokens details
            extractUsageTokenDetails(span, usage, attribute = "candidatesTokensDetails")
        }
    }

    private fun extractUsageTokenDetails(span: Span, usage: JsonElement, attribute: String) {
        // turn the given attribute into snake-cased format
        val snakeCasedAttribute = attribute.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()

        usage.jsonObject[attribute]?.let {
            // TODO: is attribute naming correct?
            for ((index, detail) in it.jsonArray.withIndex()) {
                detail.jsonObject["modality"]?.let {
                    span.setAttribute("gen_ai.usage.$snakeCasedAttribute.$index.modality", it.jsonPrimitive.content)
                }
                detail.jsonObject["tokenCount"]?.jsonPrimitive?.int?.let {
                    span.setAttribute("gen_ai.usage.$snakeCasedAttribute.$index.token_count", it.toLong())
                }
            }
        }
    }
}