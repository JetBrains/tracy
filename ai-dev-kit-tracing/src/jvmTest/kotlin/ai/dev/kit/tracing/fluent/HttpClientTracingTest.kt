package ai.dev.kit.tracing.fluent

import ai.dev.kit.HttpClientLLMProvider
import ai.dev.kit.instrument
import ai.dev.kit.tracing.BaseOpenTelemetryTracingTest
import ai.dev.kit.tracing.LITELLM_URL
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.opentelemetry.api.common.AttributeKey
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull


class HttpClientTracingTest : BaseOpenTelemetryTracingTest() {
    private fun createKtorHttpClient(): HttpClient {
        val mockEngine = MockEngine { request ->
            respond(
                content = """{"message": "hello from mock"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        // Apache5
        // val client = HttpClient(mockEngine)
        val client = HttpClient()

        return client
    }

    @Test
    fun `test Ktor HttpClient auto tracing`() = runTest {
        val client: HttpClient = instrument(createKtorHttpClient(), provider = HttpClientLLMProvider.Anthropic)

        // Trigger a request (your interceptor should be called here)
        // val response: HttpResponse = client.get("https://example.org/test")
        val model = "claude-sonnet-4-20250514"
        val response: HttpResponse = client.post("$LITELLM_URL/v1/messages") {
            val apiKey = System.getenv("LITELLM_API_KEY") ?: error("LITELLM_API_KEY environment variable is not set")

            header("x-api-key", apiKey)
            header("Content-Type", "application/json")
            setBody("""
                {
                    "max_tokens": 1024,
                    "messages": [
                        {
                            "content": "Hello, world!",
                            "role": "user"
                        }
                    ],
                    "model": "$model"
                }
            """.trimIndent())
        }

        val body: String = response.body()

        println("Response status: ${response.status}")
        println("Response body: $body")

        assertEquals(HttpStatusCode.OK, response.status)

        val traces = analyzeSpans()

        assertEquals(1, traces.size)
        val trace = traces.firstOrNull()
        assertNotNull(trace)

        assertEquals(
            LITELLM_URL,
            trace.attributes[AttributeKey.stringKey("gen_ai.api_base")]
        )

        assertTrue(
            trace.attributes[AttributeKey.stringKey("gen_ai.response.model")]?.startsWith(model) == true
        )

        val type = trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.type")]
        assertNotNull(type)
        assertTrue(type.isNotEmpty())

        val text = trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.content")]
        assertNotNull(text)
        assertTrue(text.isNotEmpty())
    }
}