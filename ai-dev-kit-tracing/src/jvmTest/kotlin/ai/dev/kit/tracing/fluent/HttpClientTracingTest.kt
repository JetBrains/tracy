package ai.dev.kit.tracing.fluent

import ai.dev.kit.HttpClientLLMProvider
import ai.dev.kit.instrument
import ai.dev.kit.tracing.BaseOpenTelemetryTracingTest
import ai.dev.kit.tracing.LITELLM_URL
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.StatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


@Tag("SkipForNonLocal")
class HttpClientTracingTest : BaseOpenTelemetryTracingTest() {
    private fun String.unquote(): String {
        if (this.startsWith("\"") && this.endsWith("\"")) {
            return this.substring(1, this.length - 1)
        }
        return this
    }

    @Test
    fun `test Ktor HttpClient auto tracing for Anthropic`() = runTest {
        val client: HttpClient = instrument(HttpClient(), provider = HttpClientLLMProvider.Anthropic)
        val model = "claude-sonnet-4-20250514"
        val promptMessage = "Hello, world!"

        val response = client.post("$LITELLM_URL/v1/messages") {
            val apiKey = System.getenv("LITELLM_API_KEY") ?: error("LITELLM_API_KEY environment variable is not set")

            header("x-api-key", apiKey)
            header("Content-Type", "application/json")
            setBody("""
                {
                    "max_tokens": 1024,
                    "messages": [
                        {
                            "content": "$promptMessage",
                            "role": "user"
                        }
                    ],
                    "model": "$model"
                }
            """.trimIndent())
        }

        val traces = analyzeSpans()

        // assert expectations on a trace
        assertEquals(1, traces.size)
        val trace = traces.firstOrNull()
        assertNotNull(trace)

        assertEquals(StatusCode.OK, trace.status.statusCode)

        assertEquals("anthropic", trace.attributes[AttributeKey.stringKey("gen_ai.system")])
        assertEquals(LITELLM_URL, trace.attributes[AttributeKey.stringKey("gen_ai.api_base")])

        val tracedModel = trace.attributes[AttributeKey.stringKey("gen_ai.response.model")]
        assertEquals(true, tracedModel?.startsWith(model))

        assertEquals(promptMessage, trace.attributes[AttributeKey.stringKey("gen_ai.prompt.0.content")]?.unquote())
        assertEquals("user", trace.attributes[AttributeKey.stringKey("gen_ai.prompt.0.role")])

        val completionType = trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.type")]
        val completionText = trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.content")]

        assertNotNull(completionType)
        assertTrue(completionType.isNotEmpty())

        assertNotNull(completionText)
        assertTrue(completionText.isNotEmpty())


        // assert that tracing doesn't consume the response body
        val responseBody = response.bodyAsText()
        assertTrue(responseBody.isNotEmpty())

        // compare trace with the actual response
        val responseJson = Json.parseToJsonElement(responseBody).jsonObject

        assertEquals(responseJson["model"]!!.jsonPrimitive.content, tracedModel)
        assertEquals(responseJson["content"]!!.jsonArray[0].jsonObject["type"]!!.jsonPrimitive.content, completionType)
        assertEquals(responseJson["content"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content, completionText.unquote())

        val usage = responseJson["usage"]!!.jsonObject
        assertEquals(
            usage["input_tokens"]!!.jsonPrimitive.int,
            trace.attributes[AttributeKey.longKey("gen_ai.usage.input_tokens")]!!.toInt()
        )
        assertEquals(
            usage["output_tokens"]!!.jsonPrimitive.int,
            trace.attributes[AttributeKey.longKey("gen_ai.usage.output_tokens")]!!.toInt()
        )
    }

    @Test
    fun `test Ktor HttpClient auto tracing for OpenAI`() = runTest {
        val client: HttpClient = instrument(HttpClient(), provider = HttpClientLLMProvider.OpenAI)
        val model = "gpt-4o-mini"
        val response = client.post("$LITELLM_URL/v1/chat/completions") {
            val apiKey = System.getenv("LITELLM_API_KEY") ?: error("LITELLM_API_KEY environment variable is not set")

            header("Authorization", "Bearer $apiKey")
            header("Content-Type", "application/json")
            setBody("""
                {
                    "messages": [
                        {
                            "role": "user",
                            "content": "hello world"
                        }
                    ],
                    "model": "$model"
                }
            """.trimIndent())
        }

        val traces = analyzeSpans()

        assertEquals(1, traces.size)
        val trace = traces.firstOrNull()
        assertNotNull(trace)

        assertEquals(StatusCode.OK, trace.status.statusCode)

        assertEquals(LITELLM_URL, trace.attributes[AttributeKey.stringKey("gen_ai.api_base")])
        assertTrue(
            trace.attributes[AttributeKey.stringKey("gen_ai.response.model")]?.startsWith(model) == true
        )

        val content = trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.content")]
        assertNotNull(content)
        assertTrue(content.isNotEmpty())

        // assert that tracing doesn't consume the response body
        assertTrue(response.bodyAsText().isNotEmpty())
    }

    // TODO: write test where a serializable object is set as a request body

    @Test
    fun `test Ktor HttpClient auto tracing for Bad Request in OpenAI`() = runTest {
        val mockedClient = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    respond(
                        content = ByteReadChannel("""
                            {
                                "error": {
                                    "message": "Bad Request Mock",
                                    "type": "exception",
                                    "param": null,
                                    "code": "invalid_request"
                                }
                            }
                        """.trimIndent()),
                        status = HttpStatusCode.BadRequest,
                        headers = headersOf(
                            HttpHeaders.ContentType,
                            ContentType.Application.Json.toString()
                        )
                    )
                }
            }
        }

        val client: HttpClient = instrument(mockedClient, provider = HttpClientLLMProvider.OpenAI)

        val response = client.post("$LITELLM_URL/v1/chat/completions") {
            val apiKey = System.getenv("LITELLM_API_KEY") ?: error("LITELLM_API_KEY environment variable is not set")

            header("Authorization", "Bearer $apiKey")
            header("Content-Type", "application/json")
            setBody("""
                {
                    "messages": [
                        {
                            "role": "user",
                            "content": "hello world"
                        }
                    ],
                    "model": "gpt-4o-mini"
                }
            """.trimIndent())
        }

        val traces = analyzeSpans()

        assertEquals(1, traces.size)
        val trace = traces.firstOrNull()
        assertNotNull(trace)

        assertEquals(StatusCode.ERROR, trace.status.statusCode)
        assertEquals(LITELLM_URL, trace.attributes[AttributeKey.stringKey("gen_ai.api_base")])

        // check error
        assertEquals("Bad Request Mock", trace.attributes[AttributeKey.stringKey("gen_ai.error.message")])
        assertEquals("invalid_request", trace.attributes[AttributeKey.stringKey("gen_ai.error.code")])
        assertEquals("exception", trace.attributes[AttributeKey.stringKey("gen_ai.error.type")])
        assertEquals(400, trace.attributes[AttributeKey.longKey("http.status_code")])

        // assert that tracing doesn't consume the response body
        assertTrue(response.bodyAsText().isNotEmpty())
    }

    @Test
    fun `test Ktor HttpClient auto tracing for Gemini`() = runTest {
        val client: HttpClient = instrument(HttpClient(), provider = HttpClientLLMProvider.Gemini)
        val model = "gemini-2.5-flash"
        val response = client.post("$LITELLM_URL/gemini/v1beta/models/$model:generateContent") {
            val apiKey = System.getenv("LITELLM_API_KEY") ?: error("LITELLM_API_KEY environment variable is not set")

            header("x-goog-api-key", apiKey)
            header("Content-Type", "application/json")
            setBody("""
                {
                    "contents": [
                        {
                            "parts": [
                                { "text": "Explain how AI works in a few words" }
                            ]
                        }
                    ]
                }
            """.trimIndent())
        }

        val traces = analyzeSpans()

        assertEquals(1, traces.size)
        val trace = traces.firstOrNull()
        assertNotNull(trace)

        assertEquals(StatusCode.OK, trace.status.statusCode)

        assertEquals(
            LITELLM_URL,
            trace.attributes[AttributeKey.stringKey("gen_ai.api_base")]
        )
        assertTrue(
            trace.attributes[AttributeKey.stringKey("gen_ai.response.model")]?.startsWith(model) == true
        )
        val text = trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.content")]
        assertNotNull(text)
        assertTrue(text.isNotEmpty())

        // assert that tracing doesn't consume the response body
        assertTrue(response.bodyAsText().isNotEmpty())
    }
}