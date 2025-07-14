package ai.dev.kit

import ai.dev.kit.tracing.AI_DEVELOPMENT_KIT_TRACER
import com.anthropic.client.AnthropicClient
import com.anthropic.client.AnthropicClientImpl
import com.anthropic.core.ClientOptions
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
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response

fun instrument(client: AnthropicClient): AnthropicClient {
    return patchClient(client, interceptor = OpenTelemetryAnthropicLogger())
}

// TODO: duplicates patchClient for OpenAIClient
private fun patchClient(client: AnthropicClient, interceptor: Interceptor): AnthropicClient {
    val clientOptionsField = AnthropicClientImpl::class.java.getDeclaredField("clientOptions").apply { isAccessible = true }
    val clientOptions = clientOptionsField.get(client)

    val originalHttpClientField = ClientOptions::class.java.getDeclaredField("originalHttpClient").apply { isAccessible = true }
    val originalHttpClient = originalHttpClientField.get(clientOptions)

    val okHttpClientField = com.anthropic.client.okhttp.OkHttpClient::class.java.getDeclaredField("okHttpClient").apply { isAccessible = true }
    val okHttpClient = okHttpClientField.get(originalHttpClient) as okhttp3.OkHttpClient

    val interceptorsField = okhttp3.OkHttpClient::class.java.getDeclaredField("interceptors").apply { isAccessible = true }

    // install tracing interceptors
    interceptorsField.set(okHttpClient, listOf(interceptor))

    return client
}


private const val SPAN_NAME = "Anthropic-generation"


// TODO: duplicates OpenTelemetryOpenAILogger
private class OpenTelemetryAnthropicLogger : Interceptor {
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

            println("ANTHROPIC REQUEST BODY: $body")
            // body?.let { getRequestBodyAttributes(span, it) }
            span.setAttribute("gen_ai.openai.api_base", "${url.scheme}://${url.host}")

            // TODO: get from parameters
            span.setAttribute(GEN_AI_SYSTEM, GenAiSystemIncubatingValues.ANTHROPIC)

            val response = chain.proceed(chain.request())

            val contentType = response.body?.contentType()

            if (contentType == "application/json".toMediaType()) {
                // We need to peek the body so the stream is not consumed
                println("ANTHROPIC RESPONSE BODY: ${response.peekBody(Long.MAX_VALUE).string()}")
                /*getResultBodyAttributes(
                    span,
                    Json.decodeFromString<JsonObject>(response.peekBody(Long.MAX_VALUE).string())
                )*/
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

    private fun getRequestBodyAttributes(span: Span, body: JsonObject) {
        body["temperature"]?.let { span.setAttribute(GEN_AI_REQUEST_TEMPERATURE, it.jsonPrimitive.content.toDouble()) }
        body["model"]?.let { span.setAttribute(GEN_AI_REQUEST_MODEL, it.jsonPrimitive.content) }

        body["messages"]?.let {
            for ((index, message) in it.jsonArray.withIndex()) {
                span.setAttribute("gen_ai.prompt.$index.role", message.jsonObject["role"]?.jsonPrimitive?.content)
                span.setAttribute("gen_ai.prompt.$index.content", message.jsonObject["content"]?.toString())
            }
        }
    }

    private fun getResultBodyAttributes(span: Span, body: JsonObject) {
        body["id"]?.let { span.setAttribute(GEN_AI_RESPONSE_ID, it.jsonPrimitive.content) }
        body["object"]?.let { span.setAttribute("llm.request.type", it.jsonPrimitive.content) }
        body["model"]?.let { span.setAttribute(GEN_AI_RESPONSE_MODEL, it.jsonPrimitive.content) }

        body["choices"]?.let {
            for ((index, choice) in it.jsonArray.withIndex()) {
                val index = choice.jsonObject["index"]?.jsonPrimitive?.int ?: index

                choice.jsonObject["message"]?.jsonObject?.let { message ->
                    span.setAttribute(
                        "gen_ai.completion.$index.role",
                        message.jsonObject["role"]?.jsonPrimitive?.content
                    )
                    span.setAttribute("gen_ai.completion.$index.content", message.jsonObject["content"]?.toString())
                    // TODO: add tool and function calling
                    /*
                    span.setAttribute("gen_ai.completion.$index.tool_calls", message.jsonObject["tool_calls"]?.jsonPrimitive?.content)
                    span.setAttribute("gen_ai.completion.$index.function_call", message.jsonObject["function_call"]?.jsonPrimitive?.content)
                    span.setAttribute("gen_ai.completion.$index.annotations", message.jsonObject["annotations"].toString())
                     */
                }

                span.setAttribute(
                    "gen_ai.completion.$index.finish_reason",
                    choice.jsonObject["finish_reason"]?.jsonPrimitive?.content
                )
            }
        }

        body["usage"]?.let { usage ->
            usage.jsonObject["prompt_tokens"]?.jsonPrimitive?.int?.let {
                span.setAttribute(
                    GEN_AI_USAGE_INPUT_TOKENS,
                    it
                )
            }
            usage.jsonObject["completion_tokens"]?.jsonPrimitive?.int?.let {
                span.setAttribute(
                    GEN_AI_USAGE_OUTPUT_TOKENS,
                    it
                )
            }
            // TODO: add other usage attributes
        }
    }
}