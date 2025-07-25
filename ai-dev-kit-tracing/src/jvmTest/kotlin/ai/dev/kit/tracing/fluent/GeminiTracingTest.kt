package ai.dev.kit.tracing.fluent

import ai.dev.kit.instrument
import ai.dev.kit.tracing.BaseOpenTelemetryTracingTest
import ai.dev.kit.tracing.autologging.createGeminiClient
import com.google.genai.errors.GenAiIOException
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.StatusCode
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.net.SocketTimeoutException
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import com.google.genai.Client as GeminiClient
import com.google.genai.types.GenerateContentConfig as GeminiGenerateContentConfig


@Tag("SkipForNonLocal")
class GeminiTracingTest() : BaseOpenTelemetryTracingTest() {
    @Test
    fun `test Gemini auto tracing`() = runTest {
        val model = "gemini-1.5-pro"
        val client = instrument(createGeminiClient())

        client.models.generateContent(
            model,
            "Generate polite greeting and introduce yourself",
            GeminiGenerateContentConfig.builder()
                .temperature(0.8f)
                .build()
        )

        val traces = analyzeSpans()

        assertEquals(1, traces.size)
        val trace = traces.firstOrNull()
        assertNotNull(trace)

        assertEquals(StatusCode.OK, trace.status.statusCode)
        assertEquals(
            LITELLM_URL,
            trace.attributes[AttributeKey.stringKey("gen_ai.gemini.api_base")]
        )
        assertTrue(
            trace.attributes[AttributeKey.stringKey("gen_ai.response.model")]?.startsWith(model) == true
        )
        val text = trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.content")]
        assertNotNull(text)
        assertTrue(text.isNotEmpty())
    }

    @Test
    fun `test Gemini span error code when timeout occurs`() {
        val client = instrument(createGeminiClient())

        val timeoutInterceptor = object : Interceptor {
            override fun intercept(chain: Interceptor.Chain): Response {
                throw SocketTimeoutException("My Custom Timeout")
            }
        }
        // the installed interceptor will imitate timeout
        installHttpInterceptor(client, interceptor = timeoutInterceptor)


        try {
            client.models.generateContent(
                "gemini-1.5-pro",
                "Generate polite greeting and introduce yourself",
                GeminiGenerateContentConfig.builder()
                    .temperature(0.8f)
                    .build()
            )
        } catch (_: GenAiIOException) {
            // suppress
        }

        val traces = analyzeSpans()

        assertEquals(1, traces.size)
        val trace = traces.firstOrNull()
        assertNotNull(trace)

        assertEquals(StatusCode.ERROR, trace.status.statusCode)
        assertEquals(
            LITELLM_URL,
            trace.attributes[AttributeKey.stringKey("gen_ai.gemini.api_base")]
        )

        val event = trace.events.firstOrNull { it.name == "exception" }
        assertNotNull(event)
        assertEquals(
            "My Custom Timeout",
            event.attributes[AttributeKey.stringKey("exception.message")],
        )
    }

    @Test
    fun `test Gemini span error code when requesting non-existent model`() {
        val client = instrument(createGeminiClient())

        try {
            client.models.generateContent(
                "[non-existent model name!]",
                "Generate polite greeting and introduce yourself",
                GeminiGenerateContentConfig.builder()
                    .temperature(0.8f)
                    .build()
            )
        } catch (e: Exception) {
            // suppress
        }

        val traces = analyzeSpans()

        assertEquals(1, traces.size)
        val trace = traces.firstOrNull()
        assertNotNull(trace)

        assertEquals(StatusCode.ERROR, trace.status.statusCode)
        assertEquals(
            LITELLM_URL,
            trace.attributes[AttributeKey.stringKey("gen_ai.gemini.api_base")]
        )

        assertTrue(trace.attributes[AttributeKey.stringKey("gen_ai.error.message")]?.isNotEmpty() == true)
        assertTrue(trace.attributes[AttributeKey.stringKey("gen_ai.error.code")]?.isNotEmpty() == true)
    }

    private fun installHttpInterceptor(client: GeminiClient, interceptor: Interceptor) {
        val apiClientField = GeminiClient::class.java.getDeclaredField("apiClient")
            .apply { isAccessible = true }
        val apiClient = apiClientField.get(client)

        val httpClientField = apiClient.javaClass.superclass.getDeclaredField("httpClient")
            .apply { isAccessible = true }
        val originalHttpClient = httpClientField.get(apiClient) as OkHttpClient

        val modifiedHttpClient = originalHttpClient.newBuilder()
            .addInterceptor(interceptor)
            .build()

        httpClientField.set(apiClient, modifiedHttpClient)
    }
}
