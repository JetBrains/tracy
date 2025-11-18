package ai.dev.kit


import ai.dev.kit.adapters.LLMTracingAdapter
import ai.dev.kit.http.parsers.MultipartFormDataParser
import ai.dev.kit.http.protocol.*
import ai.dev.kit.tracing.TracingManager
import io.ktor.http.*
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import mu.KotlinLogging
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import okhttp3.Response as OkHttpResponse
import okhttp3.ResponseBody as OkHttpResponseBody

fun instrument(client: OkHttpClient, interceptor: OpenTelemetryOkHttpInterceptor): OkHttpClient {
    val clientBuilder = client.newBuilder()
    patchInterceptorsInplace(clientBuilder.interceptors(), interceptor)
    return clientBuilder.build()
}

/**
 * Patches the OpenAI-compatible client by injecting a custom interceptor into its internal HTTP client.
 *
 * This method modifies the internal structure of the provided OpenAI-like client to replace its HTTP client interceptors
 * with the specified interceptor.
 * Supports OpenAI-compatible (**in terms of internal class structure**) clients.
 *
 * @param client The instance of the OpenAI-compatible client to patch.
 * @param interceptor The interceptor to be injected into the internal HTTP client of the OpenAI-compatible client.
 * @return The patched client instance with the custom interceptor injected into its HTTP client.
 */
fun <T> patchOpenAICompatibleClient(
    client: T,
    interceptor: Interceptor,
): T {
    val clientOptions = getFieldValue(client as Any, "clientOptions")
    val originalHttpClient = getFieldValue(clientOptions, "originalHttpClient")

    val okHttpHolder = if (originalHttpClient::class.simpleName == "OkHttpClient") {
        originalHttpClient
    } else {
        getFieldValue(originalHttpClient, "httpClient")
    }

    val okHttpClient = getFieldValue(okHttpHolder, "okHttpClient") as OkHttpClient

    // add a given interceptor if the current list of interceptors doesn't contain it already
    val updatedInterceptors = patchInterceptors(okHttpClient.interceptors, interceptor)
    setFieldValue(okHttpClient, "interceptors", updatedInterceptors)

    return client
}

fun getFieldValue(instance: Any, fieldName: String): Any {
    var cls: Class<*>? = instance.javaClass
    while (cls != null) {
        try {
            val field = cls.getDeclaredField(fieldName)
            field.isAccessible = true
            return field.get(instance) ?: throw IllegalStateException("Field '$fieldName' is null")
        } catch (_: NoSuchFieldException) {
            cls = cls.superclass
        }
    }
    throw NoSuchFieldException("Field '$fieldName' not found in ${instance.javaClass.name}")
}

fun setFieldValue(instance: Any, fieldName: String, value: Any?) {
    var cls: Class<*>? = instance.javaClass
    while (cls != null) {
        try {
            val field = cls.getDeclaredField(fieldName)
            field.isAccessible = true

            if (value == null && field.type.isPrimitive) {
                throw IllegalArgumentException("Cannot set primitive field '$fieldName' to null")
            }

            field.set(instance, value)
            return
        } catch (_: NoSuchFieldException) {
            cls = cls.superclass
        }
    }
    throw NoSuchFieldException("Field '$fieldName' not found in ${instance.javaClass.name}")
}


abstract class OpenTelemetryOkHttpInterceptor(
    private val spanName: String,
    private val adapter: LLMTracingAdapter,
) : Interceptor {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun intercept(chain: Interceptor.Chain): OkHttpResponse {
        val tracer = TracingManager.tracer

        val span = tracer.spanBuilder(spanName).startSpan()
        var isStreamingRequest = false

        span.makeCurrent().use { _ ->
            try {
                // register request
                val (bodyContent, request) = chain.request().withCopiedBodyContent()

                if (bodyContent != null) {
                    val mediaType = request.body?.contentType()
                    val req = bodyContent.asRequestBody(mediaType)?.let {
                        Request(
                            contentType = mediaType?.toContentType(),
                            url = request.url.toRequestUrl(),
                            body = it,
                        )
                    }
                    if (req != null) {
                        isStreamingRequest = adapter.isStreamingRequest(req)
                        adapter.registerRequest(span, req)
                    }
                }

                // register response
                val response = chain.proceed(request)
                val responseMediaType = response.body?.contentType()

                return if (isStreamingRequest) {
                    val streamingMarker = JsonObject(mapOf("stream" to JsonPrimitive(true)))
                    adapter.registerResponse(
                        span = span,
                        response = Response(
                            contentType = responseMediaType?.toContentType(),
                            code = response.code,
                            body = ResponseBody.Json(streamingMarker)
                        ),
                    )
                    wrapStreamingResponse(response, span)
                } else {
                    val decodedResponse = try {
                        Json.decodeFromString<JsonObject>(response.peekBody(Long.MAX_VALUE).string())
                    } catch (_: Exception) {
                        JsonObject(emptyMap())
                    }
                    adapter.registerResponse(
                        span = span,
                        response = Response(
                            contentType = responseMediaType?.toContentType(),
                            code = response.code,
                            body = ResponseBody.Json(decodedResponse)
                        ),
                    )
                    response
                }
            } catch (e: Exception) {
                span.setStatus(StatusCode.ERROR)
                span.recordException(e)
                throw e
            } finally {
                if (!isStreamingRequest) {
                    span.end()
                }
            }
        }
    }

    private fun wrapStreamingResponse(originalResponse: OkHttpResponse, span: Span): OkHttpResponse {
        val originalBody = originalResponse.body ?: return originalResponse

        val tracingBody = object : OkHttpResponseBody() {
            private val capturedText = StringBuilder()

            override fun contentType() = originalBody.contentType()
            override fun contentLength() = -1L

            override fun source(): BufferedSource {
                val originalSource = originalBody.source()

                return object : ForwardingSource(originalSource) {
                    private val acc = Buffer()
                    override fun read(sink: Buffer, byteCount: Long): Long {
                        val bytesRead = try {
                            super.read(sink, byteCount)
                        } catch (e: Exception) {
                            span.setStatus(StatusCode.ERROR)
                            span.recordException(e)
                            span.end()
                            throw e
                        }

                        if (bytesRead > 0) {
                            val start = sink.size - bytesRead
                            sink.copyTo(acc, start, bytesRead)

                            capturedText.append(acc.readUtf8(bytesRead))
                        }

                        return bytesRead
                    }
                }.buffer()
            }

            override fun close() {
                try {
                    adapter.handleStreaming(span, capturedText.toString())
                } finally {
                    span.end()
                }
            }
        }

        return originalResponse.newBuilder().body(tracingBody).build()
    }

    private fun ByteArray.asRequestBody(mediaType: MediaType?): RequestBody? {
        val bytes = this
        // check for the content type regardless of the parameters
        return when (mediaType?.toContentType()?.withoutParameters()) {
            ContentType.Application.Json -> {
                val json = try {
                    Json.parseToJsonElement(
                        bytes.toString(mediaType.charset() ?: Charsets.UTF_8)
                    ).jsonObject
                }
                catch (err: Exception) {
                    logger.trace("Error while parsing response body", err)
                    return null
                }
                RequestBody.Json(json)
            }
            ContentType.MultiPart.FormData -> {
                val parser = MultipartFormDataParser()
                val formData = parser.parse(mediaType, bytes)
                RequestBody.DataForm(formData)
            }
            else -> null
        }
    }

    private fun Request.withCopiedBodyContent(): Pair<ByteArray?, Request> {
        val body = this.body ?: return null to this
        val mediaType = body.contentType()

        // read body content
        val content = Buffer().let {
            body.writeTo(it)
            it.readByteArray()
        }

        val request = if (body.isOneShot()) {
            val newBody = content.toRequestBody(mediaType)
            this.newBuilder()
                .method(this.method, newBody)
                .build()
        } else {
            // if the body can be read multiple times,
            // then we can reuse the same request
            this
        }

        return content to request
    }
}

