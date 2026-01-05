package ai.dev.kit.tracing.fluent

import ai.dev.kit.instrument
import ai.dev.kit.tracing.BaseAITracingTest
import ai.jetbrains.tracy.anthropic.adapters.AnthropicLLMTracingAdapter
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.StatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Tag("anthropic")
class HttpClientAnthropicAITracingTest : BaseAITracingTest() {
    companion object {
        private const val ANTHROPIC_API_URL = "https://api.anthropic.com"
    }

    private val llmProviderUrl: String = System.getenv("LLM_PROVIDER_URL") ?: ANTHROPIC_API_URL
    private val llmProviderApiKey =
        System.getenv("ANTHROPIC_API_KEY") ?: System.getenv("LLM_PROVIDER_API_KEY")
        ?: error("LLM_PROVIDER_API_KEY environment variable is not set")

    @Test
    fun `test nested instrumentation calls don't cause duplicative tracing`() = runTest {
        val adapter = AnthropicLLMTracingAdapter()

        val client: HttpClient = instrument(
            instrument(
                instrument(HttpClient(), adapter),
                adapter,
            ),
            adapter,
        )
        val model = "claude-sonnet-4-20250514"
        val promptMessage = "Say: 'Hello, world!'"

        client.post("$llmProviderUrl/v1/messages") {
            header("x-api-key", llmProviderApiKey)
            header("Content-Type", "application/json")
            setBody(
                """
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
            """.trimIndent()
            )
        }

        val traces = analyzeSpans()
        assertEquals(1, traces.size)
        val trace = traces.first()

        assertEquals(StatusCode.OK, trace.status.statusCode)

        assertEquals("anthropic", trace.attributes[AttributeKey.stringKey("gen_ai.system")])
        assertEquals(llmProviderUrl, trace.attributes[AttributeKey.stringKey("gen_ai.api_base")])
    }

    @Test
    fun `test Ktor HttpClient auto tracing for Anthropic`() = runTest {
        val client: HttpClient = instrument(HttpClient(), AnthropicLLMTracingAdapter())

        val model = "claude-sonnet-4-20250514"
        val promptMessage = "Hello, world!"

        val response = client.post("$llmProviderUrl/v1/messages") {
            header("x-api-key", llmProviderApiKey)
            header("Content-Type", "application/json")
            // Required by Anthropic Messages API specification
            // See: https://docs.claude.com/en/api/messages#parameter-anthropic-version
            header("anthropic-version", "2023-06-01")
            setBody(
                """
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
            """.trimIndent()
            )
        }

        val traces = analyzeSpans()

        // assert expectations on a trace
        assertEquals(1, traces.size)
        val trace = traces.firstOrNull()
        assertNotNull(trace)

        assertEquals(StatusCode.OK, trace.status.statusCode)

        assertEquals("anthropic", trace.attributes[AttributeKey.stringKey("gen_ai.system")])
        assertEquals(llmProviderUrl, trace.attributes[AttributeKey.stringKey("gen_ai.api_base")])

        val tracedModel = trace.attributes[AttributeKey.stringKey("gen_ai.response.model")]
        assertNotNull(tracedModel)
        assertTrue(tracedModel.startsWith(model))

        assertEquals("user", trace.attributes[AttributeKey.stringKey("gen_ai.prompt.0.role")])
        assertEquals(promptMessage, trace.attributes[AttributeKey.stringKey("gen_ai.prompt.0.content")]?.unquote())

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

        assertEquals(
            responseJson["id"]!!.jsonPrimitive.content,
            trace.attributes[AttributeKey.stringKey("gen_ai.response.id")]
        )
        assertEquals(responseJson["model"]!!.jsonPrimitive.content, tracedModel)
        assertEquals(responseJson["content"]!!.jsonArray[0].jsonObject["type"]!!.jsonPrimitive.content, completionType)
        assertEquals(
            responseJson["content"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content,
            completionText.unquote()
        )

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
}
