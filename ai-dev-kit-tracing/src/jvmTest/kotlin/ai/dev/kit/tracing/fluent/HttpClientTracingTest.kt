package ai.dev.kit.tracing.fluent

import ai.dev.kit.instrument
import ai.dev.kit.tracing.BaseOpenTelemetryTracingTest
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
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
        val client = HttpClient(mockEngine)
//        HttpClient {
//
//        }
        return client
    }

    @Test
    fun `test Ktor HttpClient auto tracing`() = runTest {
        val client: HttpClient = instrument(createKtorHttpClient())

        // Trigger a request (your interceptor should be called here)
        val response: HttpResponse = client.get("https://example.org/test")
        val body: String = response.body()

        println("Response status: ${response.status}")
        println("Response body: $body")

        assertEquals(HttpStatusCode.OK, response.status)
        assert(body.contains("hello from mock"))

        val traces = analyzeSpans()

        assertEquals(1, traces.size)
        val trace = traces.firstOrNull()
        assertNotNull(trace)

        println("TRACE\n $trace")
    }
}