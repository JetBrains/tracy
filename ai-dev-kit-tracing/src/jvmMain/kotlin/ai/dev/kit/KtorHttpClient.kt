package ai.dev.kit

import ai.dev.kit.adapters.AnthropicLLMTracingAdapter
import ai.dev.kit.adapters.ContentType
import ai.dev.kit.adapters.GeminiLLMTracingAdapter
import ai.dev.kit.adapters.LLMTracingAdapter
import ai.dev.kit.adapters.OpenAILLMTracingAdapter
import ai.dev.kit.adapters.Url
import ai.dev.kit.tracing.AI_DEVELOPMENT_KIT_TRACER
import io.ktor.client.*
import io.ktor.client.plugins.api.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.StatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

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
    fun setup(config: HttpClientConfig<*>) {
        val tracer = GlobalOpenTelemetry.getTracer(AI_DEVELOPMENT_KIT_TRACER)

        val span = tracer.spanBuilder("http-client-span").startSpan()

        span.makeCurrent().use { scopeIgnored ->
            config.install(createClientPlugin("NetworkParamsPlugin") {
                onRequest { request, content ->
                    try {
                        val body = try {
                            Json.parseToJsonElement(request.body.toString()).jsonObject
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
                            Json.parseToJsonElement(response.bodyAsText()).jsonObject
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

                        span.setStatus(StatusCode.OK)
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
}

