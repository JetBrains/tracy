package ai.dev.kit

import ai.dev.kit.adapters.ContentType
import ai.dev.kit.adapters.LLMTracingAdapter
import ai.dev.kit.adapters.Url
import ai.dev.kit.tracing.TracingManager
import ai.dev.kit.tracing.fluent.processor.Span
import io.ktor.client.*
import io.ktor.client.plugins.api.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.opentelemetry.api.trace.StatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.serializer
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.starProjectedType


/**
 * Configures a Ktor [HttpClient] for tracing client calls when one of the supported LLM providers is used.
 *
 * @param client The [HttpClient] instance to be configured for tracing.
 * @param adapter The [LLMTracingAdapter] specifying the LLM adapter for which tracing should be enabled.
 * @return A configured [HttpClient] instance with tracing capabilities for the selected provider.
 */
fun instrument(client: HttpClient, adapter: LLMTracingAdapter): HttpClient {
    return client.config {
        TracingPlugin(adapter).setup(this)
    }
}

private class TracingPlugin(private val adapter: LLMTracingAdapter) {
    private val httpSpanKey = AttributeKey<Span>("HttpSpanKey")
    private val isStreamingRequestKey = AttributeKey<Boolean>("IsStreamingRequestKey")

    @OptIn(InternalAPI::class, InternalIoApi::class)
    fun setup(config: HttpClientConfig<*>) {
        val tracer = TracingManager.tracer

        config.install(createClientPlugin("NetworkParamsPlugin") {
            onRequest { request, _ ->
                val span = tracer.spanBuilder("http-client-span").startSpan()
                span.makeCurrent().use {
                    request.attributes.put(httpSpanKey, span)
                    val body = try {
                        val bodyType = request.bodyType?.type
                        when {
                            request.body is EmptyContent -> JsonObject(emptyMap())
                            (bodyType != null) && bodyType.hasAnnotation<Serializable>() -> {
                                serializeToJson(request.body)?.let { Json.parseToJsonElement(it).jsonObject }
                                    ?: JsonObject(emptyMap())
                            }

                            else -> Json.parseToJsonElement(request.body.toString()).jsonObject
                        }
                    } catch (_: Exception) {
                        JsonObject(emptyMap())
                    }

                    request.attributes.put(isStreamingRequestKey, adapter.isStreamingRequest(body))

                    adapter.registerRequest(
                        span = span, url = Url(
                            scheme = request.url.protocol.name,
                            host = request.url.host,
                            pathSegments = request.url.pathSegments
                        ), requestBody = body
                    )
                }
                span.end()
            }

            onResponse { response ->
                val isStreamingRequest = response.call.request.attributes[isStreamingRequestKey]
                val span = response.call.request.attributes[httpSpanKey]
                if (isStreamingRequest) return@onResponse
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
                } catch (exception: Exception) {
                    println(exception)
                    JsonObject(emptyMap())
                }
                adapter.registerResponse(
                    span = span,
                    contentType = response.contentType()?.let { ContentType(it.contentType, it.contentSubtype) },
                    responseCode = response.status.value.toLong(),
                    responseBody = body,
                )
                span.end()
            }

            transformResponseBody { response, content, typeInfo ->
                val isStreamingRequest = response.call.request.attributes[isStreamingRequestKey]
                val span = response.call.request.attributes[httpSpanKey]
                if (!isStreamingRequest) return@transformResponseBody null

                adapter.registerResponse(
                    span = span,
                    contentType = response.contentType()?.let { ContentType(it.contentType, it.contentSubtype) },
                    responseCode = response.status.value.toLong(),
                    responseBody = JsonObject(mapOf("stream" to JsonPrimitive(true))),
                )

                val originalBody: ByteReadChannel = content
                val tracingChannel = ByteChannel(autoFlush = true)
                val capturedText = StringBuilder()

                CoroutineScope(response.coroutineContext).launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (!originalBody.isClosedForRead) {
                            val bytesRead = originalBody.readAvailable(buffer, 0, buffer.size)
                            if (bytesRead == -1) break
                            if (bytesRead > 0) {
                                capturedText.append(buffer.decodeToString(0, bytesRead))
                                tracingChannel.writeFully(buffer, 0, bytesRead)
                                tracingChannel.flush()
                            }
                        }
                    } catch (e: Exception) {
                        span.setStatus(StatusCode.ERROR)
                        span.recordException(e)
                        if (!tracingChannel.isClosedForWrite) tracingChannel.close(e)
                    } finally {
                        try {
                            adapter.handleStreaming(span, capturedText.toString())
                        } finally {
                            span.end()
                            if (!tracingChannel.isClosedForWrite) tracingChannel.close()
                        }
                    }
                }
                if (typeInfo.type != ByteReadChannel::class) null else tracingChannel
            }
        })
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

    companion object {
        private val JSON_CONFIG = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}

