package ai.dev.kit.tracing.fluent

import ai.dev.kit.instrument
import ai.dev.kit.tracing.BaseOpenTelemetryTracingTest
import ai.dev.kit.tracing.autologging.createAnthropicClient
import com.anthropic.client.AnthropicClient
import com.anthropic.client.AnthropicClientImpl
import com.anthropic.core.ClientOptions
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.Model
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.StatusCode
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AnthropicTracingTest : BaseOpenTelemetryTracingTest() {
    @Test
    fun `test Anthropic auto tracing`() = runTest {
        val model = Model.CLAUDE_3_5_SONNET_20240620
        val client = instrument(createAnthropicClient())

        val params = MessageCreateParams.builder()
            .maxTokens(1000L)
            .temperature(0.8)
            .addUserMessage("Generate polite greeting and introduce yourself")
            .model(model)
            .build()

        client.messages().create(params)

        val traces = analyzeSpans()

        assertEquals(1, traces.size)
        val trace = traces.firstOrNull()
        assertNotNull(trace)

        assertEquals(
            LITELLM_URL,
            trace.attributes[AttributeKey.stringKey("gen_ai.anthropic.api_base")]
        )

        assertTrue(
            trace.attributes[AttributeKey.stringKey("gen_ai.response.model")]?.startsWith(model.asString()) == true
        )

        val type = trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.type")]
        assertNotNull(type)
        assertTrue(type.isNotEmpty())

        val text = trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.content")]
        assertNotNull(text)
        assertTrue(text.isNotEmpty())
    }

    @Test
    fun `test Anthropic span error status when requesting non-existent model`() = runTest {
        val client = instrument(createAnthropicClient())

        val params = MessageCreateParams.builder()
            .maxTokens(1000L)
            .temperature(0.8)
            .addUserMessage("Generate polite greeting and introduce yourself")
            .model("[non-existent model!]")
            .build()

        try {
            client.messages().create(params)
        }
        catch (_: Exception) {
            // suppress
        }

        val traces = analyzeSpans()
        val trace = traces.firstOrNull()
        assertNotNull(trace)

        assertEquals(StatusCode.ERROR, trace.status.statusCode)
        assertEquals(
            LITELLM_URL,
            trace.attributes[AttributeKey.stringKey("gen_ai.anthropic.api_base")]
        )

        assertTrue(trace.attributes[AttributeKey.stringKey("gen_ai.error.message")]?.isNotEmpty() == true)
        assertTrue(trace.attributes[AttributeKey.stringKey("gen_ai.error.code")]?.isNotEmpty() == true)
    }

    @Test
    fun `test Anthropic span error status when mocking 529 response code`() = runTest {
        val client = instrument(createAnthropicClient())
        val errorMessage = "Server is overloaded, please try again later."

        val serverOverloadedInterceptor = object : Interceptor {
            override fun intercept(chain: Interceptor.Chain): Response {
                val response = chain.proceed(chain.request())
                // see: https://docs.anthropic.com/en/api/errors
                val errorBody = """
                    {
                        "type": "error",
                        "error": {
                            "type": "overloaded_error",
                            "message": "$errorMessage"
                        }
                    }
                """.trimIndent().toResponseBody("application/json".toMediaTypeOrNull())

                return response.newBuilder()
                    .body(errorBody)
                    .code(529)
                    .build()
            }
        }

        installHttpInterceptor(client, interceptor = serverOverloadedInterceptor)

        val params = MessageCreateParams.builder()
            .maxTokens(1000L)
            .temperature(0.8)
            .addUserMessage("Generate polite greeting and introduce yourself")
            .model("[non-existent model!]")
            .build()

        try {
            client.messages().create(params)
        }
        catch (_: Exception) {
            // suppress
        }

        val traces = analyzeSpans()
        val trace = traces.firstOrNull()
        assertNotNull(trace)

        assertEquals(StatusCode.ERROR, trace.status.statusCode)
        assertEquals(
            LITELLM_URL,
            trace.attributes[AttributeKey.stringKey("gen_ai.anthropic.api_base")]
        )

        assertEquals(errorMessage, trace.attributes[AttributeKey.stringKey("gen_ai.error.message")])
        assertEquals(529, trace.attributes[AttributeKey.longKey("http.status_code")])
    }

    private fun installHttpInterceptor(client: AnthropicClient, interceptor: Interceptor) {
        val clientOptionsField = AnthropicClientImpl::class.java.getDeclaredField("clientOptions").apply { isAccessible = true }
        val clientOptions = clientOptionsField.get(client)

        val originalHttpClientField = ClientOptions::class.java.getDeclaredField("originalHttpClient").apply { isAccessible = true }
        val originalHttpClient = originalHttpClientField.get(clientOptions)

        val okHttpClientField = com.anthropic.client.okhttp.OkHttpClient::class.java.getDeclaredField("okHttpClient").apply { isAccessible = true }
        val okHttpClient = okHttpClientField.get(originalHttpClient) as OkHttpClient

        val modifiedHttpClient = okHttpClient.newBuilder()
            .addInterceptor(interceptor)
            .build()

        okHttpClientField.set(originalHttpClient, modifiedHttpClient)
    }
}