package ai.dev.kit

import ai.dev.kit.tracing.AI_DEVELOPMENT_KIT_TRACER
import io.ktor.client.*
import io.ktor.client.plugins.api.*
import io.ktor.client.statement.*
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.StatusCode

fun instrument(client: HttpClient): HttpClient {
    return patchClient(client/*, interceptor = TODO()*/)
}

private fun patchClient(client: HttpClient /*interceptor: String*/): HttpClient {

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

                        span.setAttribute("gen_ai.api_base", "request")
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
                        span.setAttribute("http.status_code", "response")
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

