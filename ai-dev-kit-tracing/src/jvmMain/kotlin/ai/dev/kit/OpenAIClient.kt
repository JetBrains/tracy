package ai.dev.kit

import ai.dev.kit.exporters.createLangfuseExporter
import ai.dev.kit.tracing.AI_DEVELOPMENT_KIT_TRACER
import com.openai.client.OpenAIClient
import com.openai.client.OpenAIClientImpl
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.core.ClientOptions
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.*
import kotlinx.serialization.json.*
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response

@Deprecated("instrument() instead")
fun createOpenAIClient(): OpenAIClient {
    val openAIClient = OpenAIOkHttpClient.builder()
        .fromEnv()
        .build().apply {
            patchClient(this, interceptor = OpenTelemetryOpenAILogger())
        }

    return openAIClient
}

fun instrument(
    client: OpenAIClient,
    langfuseUrl: String? = null,
    langfusePublicKey: String? = null,
    langfuseSecretKey: String? = null,
): OpenAIClient {
    val interceptor = OpenTelemetryOpenAILogger(langfuseUrl, langfusePublicKey, langfuseSecretKey)
    return patchClient(client, interceptor)
}

private fun patchClient(openAIClient: OpenAIClient, interceptor: Interceptor): OpenAIClient {
    // install custom interceptor into an HTTP client
    val clientOptionsField =
        OpenAIClientImpl::class.java.getDeclaredField("clientOptions").apply { isAccessible = true }
    val clientOptions = clientOptionsField.get(openAIClient)

    val originalHttpClientField =
        ClientOptions::class.java.getDeclaredField("originalHttpClient").apply { isAccessible = true }
    val originalHttpClient = originalHttpClientField.get(clientOptions)

    val okHttpClientField =
        com.openai.client.okhttp.OkHttpClient::class.java.getDeclaredField("okHttpClient").apply { isAccessible = true }
    val okHttpClient = okHttpClientField.get(originalHttpClient) as OkHttpClient

    val interceptorsField = OkHttpClient::class.java.getDeclaredField("interceptors").apply { isAccessible = true }

    interceptorsField.set(okHttpClient, listOf(interceptor))

    return openAIClient
}

private const val SPAN_NAME = "OpenAI-generation"


class OpenTelemetryOpenAILogger(
    langfuseUrl: String? = null,
    langfusePublicKey: String? = null,
    langfuseSecretKey: String? = null,
) : Interceptor {
    private val openTelemetry: OpenTelemetry by lazy {
        initializeOpenTelemetry(langfuseUrl, langfusePublicKey, langfuseSecretKey)
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        // val tracer = GlobalOpenTelemetry.getTracer(AI_DEVELOPMENT_KIT_TRACER)
        val tracer = openTelemetry.getTracer(AI_DEVELOPMENT_KIT_TRACER)
        val span = tracer.spanBuilder(SPAN_NAME).startSpan()

        println("STARTED SPAN: $span")
        span.makeCurrent().use { scopeIgnored ->
            try {
                val request = chain.request()
                val url = request.url
                val body = request.body?.let {
                    val buffer = okio.Buffer()
                    it.writeTo(buffer)
                    Json.parseToJsonElement(buffer.readUtf8()).jsonObject
                }

                  body?.let { getRequestBodyAttributes(span, it) }
                 span.setAttribute("gen_ai.openai.api_base", "${url.scheme}://${url.host}")

                // TODO: get from parameters
                span.setAttribute(GEN_AI_SYSTEM, GenAiSystemIncubatingValues.OPENAI)

                // scrapping data from response
                val response = chain.proceed(chain.request())

                val requiredContentType = "application/json".toMediaType()
                val contentType = response.body?.contentType()

                if (contentType?.type == requiredContentType.type && contentType.subtype == requiredContentType.subtype) {
                    // we need to peek the body so the stream is not consumed
                    val decodedResponse = Json.decodeFromString<JsonObject>(response.peekBody(Long.MAX_VALUE).string())
                     getResultBodyAttributes(span, decodedResponse)
                } else {
                    contentType?.let { span.setAttribute("gen_ai.completion.content.type", it.toString()) }
                }

                span.setStatus(StatusCode.OK)
                return response
            }
            catch (err: Exception) {
                span.setStatus(StatusCode.ERROR)
                span.recordException(err)
                throw err
            }
            finally {
                span.end()
                println("CLOSING SPAN")
            }
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

    private fun initializeOpenTelemetry(
        langfuseUrl: String? = null,
        langfusePublicKey: String? = null,
        langfuseSecretKey: String? = null,
    ): OpenTelemetry {
        /*val resource = Resource.getDefault().merge(
            Resource.create(
                Attributes.builder()
                    .put(AttributeKey.stringKey("ai.devkit.version"), "0.0.1")
                    .put(AttributeKey.stringKey("ai.devkit.origin"), ai.devkit.openai.client")
                    .put(AttributeKey.stringKey("ai.devkit.type"), "openai"")
                    .build()
            )
        )*/

        val resource = Resource.getDefault()

        /*
        val privateKey = "..."
        val publicKey = "..."

        val credentials = "$publicKey:$privateKey"
        val encodedAuth = Base64.getEncoder().encodeToString(credentials.toByteArray())
        println("LANGFUSE_AUTH: $encodedAuth")

        val spanExporter = OtlpHttpSpanExporter.builder()
            // api/public/otel, api/public/ingestion, api/public/otel/v1/traces
            .setEndpoint("https://langfuse.labs.jb.gg/api/public/otel")
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Basic $encodedAuth")
            .build()
        */

        val spanExporter = createLangfuseExporter(langfuseUrl, langfusePublicKey, langfuseSecretKey)
            ?: error("Failed to create Langfuse Span Exporter")

        println("CREATING CUSTOM spanExporter: $spanExporter")

        val tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
            .setResource(resource)
            .build()

        return OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .build()
    }
}