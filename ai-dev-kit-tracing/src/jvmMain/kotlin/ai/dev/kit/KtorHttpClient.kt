package ai.dev.kit

import ai.dev.kit.adapters.*
import ai.dev.kit.adapters.ContentType
import ai.dev.kit.adapters.Url
import ai.dev.kit.tracing.TracingManager
import io.ktor.client.*
import io.ktor.client.plugins.api.*
import io.ktor.client.utils.EmptyContent
import io.ktor.http.*
import io.ktor.utils.io.*
import io.opentelemetry.api.trace.StatusCode
import kotlinx.io.Buffer
import kotlinx.io.InternalIoApi
import kotlinx.io.readString
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.serializer
import kotlinx.serialization.serializerOrNull
import kotlin.reflect.KClass
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

        span.makeCurrent().use { scopeIgnored ->
            config.install(createClientPlugin("NetworkParamsPlugin") {
                onRequest { request, _ ->
                    try {
                        val body = try {
                            val bodyType = request.bodyType?.type
                            when {
                                request.body is EmptyContent -> JsonObject(emptyMap())
                                (bodyType != null) && isAnnotatedAsSerializable(bodyType) -> {
                                    serializeToJson(request.body)
                                        ?.let { Json.parseToJsonElement(it).jsonObject }
                                        ?: JsonObject(emptyMap())
                                }
                                else -> Json.parseToJsonElement(request.body.toString()).jsonObject
                            }
                        } catch (_: Exception) {
                            JsonObject(emptyMap())
                        }

                        adapter.registerRequest(
                            span = span,
                            url = Url(
                                scheme = request.url.protocol.name,
                                host = request.url.host,
                                pathSegments = request.url.pathSegments,
                            ),
                            requestBody = body
                        )
                    }
                    catch (e: Exception) {
                        span.setStatus(StatusCode.ERROR)
                        span.recordException(e)
                        span.end()
                        throw e
                    }
                }

                onResponse { response ->
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
                        }
                        catch (_: Exception) {
                            JsonObject(emptyMap())
                        }

                        adapter.registerResponse(
                            span = span,
                            contentType = response.contentType()?.let { ContentType(it.contentType, it.contentSubtype) },
                            responseCode = response.status.value.toLong(),
                            responseBody = body,
                        )
                    }
                    catch (e: Exception) {
                        span.setStatus(StatusCode.ERROR)
                        span.recordException(e)
                        throw e
                    } finally {
                        span.end()
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

            if (isAnnotatedAsSerializable(kClass)) {
                JSON_CONFIG.encodeToString(Json.serializersModule.serializer(obj::class.starProjectedType), obj)
                // NOTE: we perform an unsafe cast that's actually safe
                // because we know the serializer matches the object's actual type
                /*val serializer = kClass.serializerOrNull()?.let {
                    @Suppress("UNCHECKED_CAST")
                    it as? KSerializer<Any>
                }

                if (serializer != null) {
                    JSON_CONFIG.encodeToString(serializer, obj)
                }
                else {
                    null
                }*/
            }
            else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Checks if a given [KClass] is marked with `@Serializable` annotation.
     */
    private fun isAnnotatedAsSerializable(kClass: KClass<*>): Boolean {
        return kClass.hasAnnotation<Serializable>()
    }

    companion object {
        private val JSON_CONFIG = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}

