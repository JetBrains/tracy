package ai.dev.kit

import ai.dev.kit.adapters.*
import ai.dev.kit.adapters.ContentType
import ai.dev.kit.adapters.Url
import ai.dev.kit.tracing.TracingManager
import io.ktor.client.*
import io.ktor.client.call.save
import io.ktor.client.plugins.api.*
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.io.Buffer
import kotlinx.io.InternalIoApi
import kotlinx.io.readString
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.starProjectedType

/**
 * Selection of the supported LLM providers that can be
 * instrumented for tracing when Ktor's `HttpClient` is
 * used under the hood.
 *
 * @see ai.dev.kit.instrument
 */
enum class HttpClientLLMProvider {
    OpenAI,
    Anthropic,
    Gemini,
    Grazie
}

/**
 * Configures a Ktor `HttpClient` for tracing client calls when one of the supported LLM providers is used.
 *
 * @param client The `HttpClient` instance to be configured for tracing.
 * @param provider The `HttpClientLLMProvider` specifying the LLM provider for which tracing should be enabled.
 * @return A configured `HttpClient` instance with tracing capabilities for the selected provider.
 */
fun instrument(client: HttpClient, provider: HttpClientLLMProvider): HttpClient {
    val adapter = when (provider) {
        HttpClientLLMProvider.OpenAI -> OpenAILLMTracingAdapter()
        HttpClientLLMProvider.Anthropic -> AnthropicLLMTracingAdapter()
        HttpClientLLMProvider.Gemini -> GeminiLLMTracingAdapter()
        HttpClientLLMProvider.Grazie -> GrazieLLMTracingAdapter()
    }

    return client.config {
        TracingPlugin(adapter).setup(this)
    }
}

private class TracingPlugin(private val adapter: LLMTracingAdapter) {
    @OptIn(InternalAPI::class, InternalIoApi::class)
    fun setup(config: HttpClientConfig<*>) {
        val tracer = TracingManager.tracer

        val span = tracer.spanBuilder("http-client-span").startSpan()
        var isStreamingRequest = false

        span.makeCurrent().use { scopeIgnored ->
            config.install(createClientPlugin("NetworkParamsPlugin") {
                onRequest { request, _ ->
                    try {
                        val body = try {
                            val bodyType = request.bodyType?.type
                            when {
                                request.body is EmptyContent -> JsonObject(emptyMap())
                                (bodyType != null) && bodyType.hasAnnotation<Serializable>() -> {
                                    serializeToJson(request.body)
                                        ?.let { Json.parseToJsonElement(it).jsonObject }
                                        ?: JsonObject(emptyMap())
                                }

                                else -> Json.parseToJsonElement(request.body.toString()).jsonObject
                            }
                        } catch (_: Exception) {
                            JsonObject(emptyMap())
                        }

                        isStreamingRequest =
                            body["stream"]?.jsonPrimitive?.boolean == true || request.url.toString().contains("stream")

                        adapter.registerRequest(
                            span = span,
                            url = Url(
                                scheme = request.url.protocol.name,
                                host = request.url.host,
                                pathSegments = request.url.pathSegments,
                            ),
                            requestBody = body
                        )
                    } catch (e: Exception) {
                        span.setStatus(StatusCode.ERROR)
                        span.recordException(e)
                        span.end()
                        throw e
                    }
                }

                onResponse { response ->
                    if (isStreamingRequest) {
                        val streamingMarker = JsonObject(mapOf("stream" to JsonPrimitive(true)))

                        adapter.registerResponse(
                            span = span,
                            contentType = response.contentType()
                                ?.let { ContentType(it.contentType, it.contentSubtype) },
                            responseCode = response.status.value.toLong(),
                            responseBody = streamingMarker,
                        )

                        wrapStreamingResponse(response, span)
                        return@onResponse
                    }
                    try {
                        val body = try {
                            // peek the response body to avoid consuming the underlying channel
                            val responseString = run {
                                // NOTE: we must first peek and only then await.
                                // otherwise there are cases when an empty body gets peeked
                                val peeked = response.rawContent.readBuffer.peek()
                                response.rawContent.awaitContent(Int.MAX_VALUE)
                                peeked.request(Long.MAX_VALUE)
                                val buffer = Buffer()
                                buffer.write(peeked, peeked.buffer.size)
                                buffer.readString()
                            }
                            Json.parseToJsonElement(responseString).jsonObject
                        } catch (_: Exception) {
                            JsonObject(emptyMap())
                        }

                        adapter.registerResponse(
                            span = span,
                            contentType = response.contentType()
                                ?.let { ContentType(it.contentType, it.contentSubtype) },
                            responseCode = response.status.value.toLong(),
                            responseBody = body,
                        )
                    } catch (e: Exception) {
                        span.setStatus(StatusCode.ERROR)
                        span.recordException(e)
                        throw e
                    } finally {
                        if (!isStreamingRequest) span.end()
                    }
                }
            })
        }
    }

    /**
     * Helper function to serialize `@Serializable` objects with an unknown type
     */
    @OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
    fun serializeToJson(obj: Any): String? {
        return try {
            val kClass = obj::class

            if (kClass.hasAnnotation<Serializable>()) {
                JSON_CONFIG.encodeToString(Json.serializersModule.serializer(kClass.starProjectedType), obj)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
    private suspend fun wrapStreamingResponse(originalResponse: HttpResponse, span: Span) {
        val savedCall = originalResponse.call.save()
        val upstream = savedCall.response.bodyAsChannel()
        val channel = ByteChannel(autoFlush = true)
        val capturedText = StringBuilder()

        CoroutineScope(originalResponse.coroutineContext).launch {
            try {
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (!upstream.isClosedForRead) {
                    val bytesRead = upstream.readAvailable(buffer, 0, buffer.size)
                    if (bytesRead == -1) break
                    if (bytesRead > 0) {
                        capturedText.append(buffer.decodeToString(0, bytesRead))
                        channel.writeFully(buffer, 0, bytesRead)
                        channel.flush()
                    }
                }
                upstream.cancel()
            } catch (e: Exception) {
                span.setStatus(StatusCode.ERROR)
                span.recordException(e)
                if (!channel.isClosedForWrite) channel.close(e)
                throw e
            } finally {
                try {
                    adapter.handleStreaming(span, capturedText.toString())
                } finally {
                    span.end()
                }
                if (!channel.isClosedForWrite) channel.close()
            }
        }
    }

    companion object {
        private val JSON_CONFIG = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}

