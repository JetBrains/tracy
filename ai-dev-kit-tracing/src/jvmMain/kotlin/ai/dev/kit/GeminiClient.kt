package ai.dev.kit

import ai.dev.kit.tracing.AI_DEVELOPMENT_KIT_TRACER
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_MODEL
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_TEMPERATURE
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_RESPONSE_ID
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_RESPONSE_MODEL
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_SYSTEM
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_USAGE_INPUT_TOKENS
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_USAGE_OUTPUT_TOKENS
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GenAiSystemIncubatingValues
import kotlinx.serialization.json.Json
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

class OpenTelemetryGeminiLogger : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val tracer = GlobalOpenTelemetry.getTracer(AI_DEVELOPMENT_KIT_TRACER)

        val span = tracer.spanBuilder(SPAN_NAME).startSpan()
        val scope = span.makeCurrent()

        try {
            val request = chain.request()
            val url = request.url
            val body = request.body?.let {
                val buffer = okio.Buffer()
                it.writeTo(buffer)
                Json.parseToJsonElement(buffer.readUtf8()).jsonObject
            }

            println("GEMINI REQUEST BODY: $body")
            body?.let { getRequestBodyAttributes(span, url, it) }
            println("GEMINI URL: ${url} (pathSegments: ${url.pathSegments})")
            span.setAttribute("gen_ai.gemini.api_base", "${url.scheme}://${url.host}")

            // TODO: get from parameters
            span.setAttribute(GEN_AI_SYSTEM, GenAiSystemIncubatingValues.GEMINI)

            val response = chain.proceed(chain.request())

            val contentType = response.body?.contentType()
            println("GEMINI CONTENT TYPE: $contentType (isJson=${contentType == "application/json".toMediaType()}), ${"application/json".toMediaType()}")

            val requiredMediaType = "application/json".toMediaType()

            if (contentType?.type == requiredMediaType.type &&
                contentType.subtype == requiredMediaType.subtype) {
                // We need to peek the body so the stream is not consumed
                println("GEMINI RESPONSE BODY: ${response.peekBody(Long.MAX_VALUE).string()}")

                val decodedResponse = Json.decodeFromString<JsonObject>(response.peekBody(Long.MAX_VALUE).string())
                getResultBodyAttributes(span, decodedResponse)
            } else {
                contentType?.let { span.setAttribute("gen_ai.completion.content.type", it.toString()) }
            }

            return response
        } catch (e: Exception) {
            throw e
        } finally {
            scope.close()
            span.end()
        }
    }

    private fun getRequestBodyAttributes(span: Span, url: HttpUrl, body: JsonObject) {
        body["generationConfig"]?.let {
            it.jsonObject["temperature"]?.jsonPrimitive?.let { temperature ->
                span.setAttribute(GEN_AI_REQUEST_TEMPERATURE, temperature.double)
            }
        }

        // contents: [ { parts: array, role: str } ]
        body["contents"]?.let {
            for ((index, message) in it.jsonArray.withIndex()) {
                // role
                span.setAttribute("gen_ai.prompt.$index.role", message.jsonObject["role"]?.jsonPrimitive?.content)

                // prompt parts
                // parts: [ { text: str } ]
                val parts = message.jsonObject["parts"]?.jsonArray ?: emptyList()
                for ((partIndex, part) in parts.withIndex()) {
                    val text = part.jsonObject["text"]?.jsonPrimitive?.content ?: continue
                    span.setAttribute("gen_ai.prompt.$index.parts.$partIndex.text", text)
                }
            }
        }

        body["generationConfig"]?.let {
            it.jsonObject["temperature"]?.let { span.setAttribute(GEN_AI_REQUEST_TEMPERATURE, it.jsonPrimitive.content.toDouble()) }

            val model = url.pathSegments.lastOrNull()?.let { it.split(":").firstOrNull() ?: it }
            body["model"]?.let { span.setAttribute(GEN_AI_REQUEST_MODEL, model) }
            // TODO: other fields
        }
    }

    private fun getResultBodyAttributes(span: Span, body: JsonObject) {
        body["responseId"]?.let { span.setAttribute(GEN_AI_RESPONSE_ID, it.jsonPrimitive.content) }
        body["modelVersion"]?.let { span.setAttribute(GEN_AI_RESPONSE_MODEL, it.jsonPrimitive.content) }

        body["candidates"]?.let {
            for ((index, candidate) in it.jsonArray.withIndex()) {
                val index = candidate.jsonObject["index"]?.jsonPrimitive?.int ?: index

                candidate.jsonObject["content"]?.let { content ->
                    // role
                    span.setAttribute(
                        "gen_ai.completion.$index.role",
                        content.jsonObject["role"]?.jsonPrimitive?.content
                    )

                    // response parts
                    val parts = content.jsonObject["parts"]?.jsonArray?.withIndex() ?: emptyList()
                    for ((partIndex, part) in parts) {
                        val text = part.jsonObject["text"]?.jsonPrimitive?.content ?: continue
                        span.setAttribute("gen_ai.completion.$index.parts.$partIndex.text", text)
                    }

                    // TODO: add tool and function calling
                    /*
                    span.setAttribute("gen_ai.completion.$index.tool_calls", message.jsonObject["tool_calls"]?.jsonPrimitive?.content)
                    span.setAttribute("gen_ai.completion.$index.function_call", message.jsonObject["function_call"]?.jsonPrimitive?.content)
                    span.setAttribute("gen_ai.completion.$index.annotations", message.jsonObject["annotations"].toString())
                     */
                }

                // finish reason
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
            // TODO: add other usage attributes
        }
    }
}