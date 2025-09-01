package ai.dev.kit

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

fun instrument(client: HttpClient): HttpClient {
    return patchClient(client/*, interceptor = TODO()*/)
}

private fun patchClient(client: HttpClient /*interceptor: String*/): HttpClient {
//    client.plugin(HttpSend).intercept { request ->
//        execute(request)
//    }

    return client.config {
        NetworkParamsPlugin().setup(this)
    }

//    val clientClass: Class<HttpClient> = client.javaClass
//    val configField = clientClass.getDeclaredField("config").apply { isAccessible = true }
//    val config = configField.get(client) as HttpClientConfig<*> // HttpClientConfig<HttpClientEngineConfig>
//
//    val configClass = config.javaClass
//    val customInterceptorsField = configClass.getDeclaredField("customInterceptors").apply { isAccessible = true }
//    val customInterceptors = customInterceptorsField.get(config) as MutableMap<String, (HttpClient) -> Unit>
//
//    customInterceptors.put("Value123") {
//        println("HIHIHI!")
//        println("In interceptor:\n$it")
//    }
//
//    // client.config.customInterceptors.put("") { httpClient -> }
//    println("[HERE] customInterceptors: $customInterceptors")

    return client
}

class NetworkParamsPlugin {
    private val adapter = AnthropicAdapter()

    fun setup(config: HttpClientConfig<*>) {
        val tracer = GlobalOpenTelemetry.getTracer(AI_DEVELOPMENT_KIT_TRACER)

        val span = tracer.spanBuilder("http-client-span").startSpan()

        span.makeCurrent().use { scopeIgnored ->
            config.install(createClientPlugin("NetworkParamsPlugin") {
                onRequest { request, content ->
                    try {
                        println("[LISTENER body]: ${request.body}")
                        println("[LISTENER content]: $content")
                        println("Attributes: ${request.attributes}")

                        val body = try {
                            Json.parseToJsonElement(request.body.toString()).jsonObject
                        } catch (_: Exception) {
                            JsonObject(emptyMap())
                        }

                        adapter.registerRequest(
                            span = span,
                            url = Url(scheme = request.url.protocol.name, host = request.url.host),
                            requestBody = body
                        )
                    }
                    catch (e: Exception) {
                        println("Error")
                        span.setStatus(StatusCode.ERROR)
                        span.recordException(e)
                        span.end()
                        throw e
                    }
                }

                onResponse { response ->
                    try {
                        println("[LISTENER response]: ${response.bodyAsText()}")

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
                        println("Error")
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

