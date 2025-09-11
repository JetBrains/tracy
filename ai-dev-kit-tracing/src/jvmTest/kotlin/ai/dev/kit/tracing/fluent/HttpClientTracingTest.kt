package ai.dev.kit.tracing.fluent

import ai.dev.kit.HttpClientLLMProvider
import ai.dev.kit.instrument
import ai.dev.kit.tracing.BaseOpenTelemetryTracingTest
import ai.dev.kit.tracing.LITELLM_URL
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_MODEL
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_TEMPERATURE
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


@Tag("SkipForNonLocal")
class HttpClientTracingTest : BaseOpenTelemetryTracingTest() {
    @Test
    fun `test Ktor HttpClient auto tracing for Anthropic`() = runTest {
        val client: HttpClient = instrument(HttpClient(), provider = HttpClientLLMProvider.Anthropic)
        val model = "claude-sonnet-4-20250514"
        val promptMessage = "Hello, world!"

        val response = client.post("$LITELLM_URL/v1/messages") {
            val apiKey = System.getenv("LITELLM_API_KEY") ?: error("LITELLM_API_KEY environment variable is not set")

            header("x-api-key", apiKey)
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

        // assert expectations on a trace
        assertEquals(1, traces.size)
        val trace = traces.firstOrNull()
        assertNotNull(trace)

        assertEquals(StatusCode.OK, trace.status.statusCode)

        assertEquals("anthropic", trace.attributes[AttributeKey.stringKey("gen_ai.system")])
        assertEquals(LITELLM_URL, trace.attributes[AttributeKey.stringKey("gen_ai.api_base")])

        val tracedModel = trace.attributes[AttributeKey.stringKey("gen_ai.response.model")]
        assertEquals(true, tracedModel?.startsWith(model))

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

    @ParameterizedTest
    @MethodSource("provideTestParameters")
    fun `test Ktor HttpClient auto tracing with different request body types for OpenAI`(
        testName: String,
        prompt: String,
        model: String,
        requestBody: Any,
    ) = runTest {
        val client: HttpClient = instrument(HttpClient {
            install(ContentNegotiation) {
                json(Json { prettyPrint = true })
            }
        }, provider = HttpClientLLMProvider.OpenAI)

        val response = client.post("$LITELLM_URL/v1/chat/completions") {
            val apiKey = System.getenv("LITELLM_API_KEY") ?: error("LITELLM_API_KEY environment variable is not set")

            header("Authorization", "Bearer $apiKey")
            header("Content-Type", "application/json")
            when (requestBody) {
                // for the request.bodyType to be set correctly
                is Request -> setBody<Request>(requestBody)
                is String -> setBody<String>(requestBody)
                else -> setBody(requestBody)
            }
        }

        val traces = analyzeSpans()

        assertEquals(1, traces.size)
        val trace = traces.firstOrNull()
        assertNotNull(trace)

        assertEquals(StatusCode.OK, trace.status.statusCode)

        assertEquals("openai", trace.attributes[AttributeKey.stringKey("gen_ai.system")])
        assertEquals(LITELLM_URL, trace.attributes[AttributeKey.stringKey("gen_ai.api_base")])

        val tracedModel = trace.attributes[AttributeKey.stringKey("gen_ai.response.model")]
        assertEquals(true, tracedModel?.startsWith(model))

        assertEquals("user", trace.attributes[AttributeKey.stringKey("gen_ai.prompt.0.role")])
        assertEquals(prompt, trace.attributes[AttributeKey.stringKey("gen_ai.prompt.0.content")]?.unquote())

        val completionRole = trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.role")]
        val completionContent = trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.content")]

        assertNotNull(completionRole)
        assertTrue(completionRole.isNotEmpty())

        assertNotNull(completionContent)
        assertTrue(completionContent.isNotEmpty())

        // assert that tracing doesn't consume the response body
        val responseBody = response.bodyAsText()
        assertTrue(responseBody.isNotEmpty())

        val responseJson = Json.parseToJsonElement(responseBody).jsonObject

        assertEquals(
            responseJson["id"]!!.jsonPrimitive.content,
            trace.attributes[AttributeKey.stringKey("gen_ai.response.id")]
        )
        assertEquals(responseJson["model"]!!.jsonPrimitive.content, tracedModel)
        assertEquals(
            responseJson["choices"]?.jsonArray[0]?.jsonObject["message"]?.jsonObject["role"]?.jsonPrimitive?.content,
            completionRole
        )
        assertEquals(
            responseJson["choices"]?.jsonArray[0]?.jsonObject["message"]?.jsonObject["content"]?.jsonPrimitive?.content,
            completionContent.unquote()
        )
        assertEquals(
            responseJson["usage"]!!.jsonObject["prompt_tokens"]!!.jsonPrimitive.int,
            trace.attributes[AttributeKey.longKey("gen_ai.usage.input_tokens")]!!.toInt()
        )
        assertEquals(
            responseJson["usage"]!!.jsonObject["completion_tokens"]!!.jsonPrimitive.int,
            trace.attributes[AttributeKey.longKey("gen_ai.usage.output_tokens")]!!.toInt()
        )
    }

    @Test
    fun `test Ktor HttpClient auto tracing streaming for OpenAI`() = runTest {
        val client: HttpClient = instrument(HttpClient(), provider = HttpClientLLMProvider.OpenAI)
        val model = "gpt-4o-mini"
        client.post("$LITELLM_URL/v1/chat/completions") {
            val apiKey = System.getenv("LITELLM_API_KEY") ?: error("LITELLM_API_KEY environment variable is not set")

            header("Authorization", "Bearer $apiKey")
            header("Content-Type", "application/json")
            header("Accept", "text/event-stream")
            setBody("""
                {
                    "messages": [
                        {
                            "role": "user",
                            "content": "hello world"
                        }
                    ],
                    "model": "$model",
                    "stream": true
                }
            """.trimIndent())
        }

        val traces = analyzeSpans()

        assertEquals(1, traces.size)
        val trace = traces.firstOrNull()
        assertNotNull(trace)

        val content = trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.content")]
        assertNotNull(content)
        assertTrue(content.isNotEmpty())
    }

    @Test
    fun `test Ktor HttpClient auto tracing for Bad Request in OpenAI`() = runTest {
        val mockedClient = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    respond(
                        content = ByteReadChannel(
                            """
                            {
                                "error": {
                                    "message": "Bad Request Mock",
                                    "type": "exception",
                                    "param": null,
                                    "code": "invalid_request"
                                }
                            }
                        """.trimIndent()
                        ),
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
            setBody(
                """
                {
                    "messages": [
                        {
                            "role": "user",
                            "content": "hello world"
                        }
                    ],
                    "model": "gpt-4o-mini"
                }
            """.trimIndent()
            )
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
    fun `test tracing for OpenAI doesn't fail when all properties are null`() = runTest {
        val mockedClient = HttpClient(MockEngine) {
            engine {
                addHandler { _ ->
                    respond(
                        content = ByteReadChannel(
                            """
                                {
                                  "id": null,
                                  "object": "chat.completion",
                                  "model": null,
                                  "choices": [
                                    {
                                      "index": null,
                                      "message": {
                                        "role": null,
                                        "content": null,
                                        "refusal": null,
                                        "annotations": null
                                      },
                                      "logprobs": null,
                                      "finish_reason": null
                                    }
                                  ],
                                  "usage": {
                                    "prompt_tokens": null,
                                    "completion_tokens": null,
                                    "total_tokens": null,
                                    "prompt_tokens_details": {
                                      "cached_tokens": null,
                                      "audio_tokens": null
                                    }
                                  },
                                  "service_tier": null
                                }
                            """.trimIndent()
                        ),
                        status = HttpStatusCode.OK,
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
            setBody(
                """
                {
                    "messages": [
                        {
                            "role": null,
                            "content": null
                        }
                    ],
                    "model": null,
                    "temperature": null
                }
            """.trimIndent()
            )
        }

        val traces = analyzeSpans()

        assertEquals(1, traces.size)
        val trace = traces.firstOrNull()
        assertNotNull(trace)

        assertEquals(StatusCode.OK, trace.status.statusCode)
        assertEquals(LITELLM_URL, trace.attributes[AttributeKey.stringKey("gen_ai.api_base")])

        assertEquals("null", trace.attributes[AttributeKey.stringKey("gen_ai.prompt.0.role")])
        assertEquals("null", trace.attributes[AttributeKey.stringKey("gen_ai.prompt.0.content")])
        assertEquals(null, trace.attributes[GEN_AI_REQUEST_TEMPERATURE])
        assertEquals("null", trace.attributes[GEN_AI_REQUEST_MODEL])
        assertEquals("null", trace.attributes[AttributeKey.stringKey("gen_ai.response.model")])
        assertEquals("null", trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.role")])
        assertEquals("null", trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.content")])
        assertEquals("null", trace.attributes[AttributeKey.stringKey("gen_ai.response.id")])
    }

    @Test
    fun `test Ktor HttpClient auto tracing for Gemini`() = runTest {
        val client: HttpClient = instrument(HttpClient(), provider = HttpClientLLMProvider.Gemini)
        val model = "gemini-2.5-flash"
        val promptMessage = "Explain how AI works in a few words"

        val response = client.post("$LITELLM_URL/gemini/v1beta/models/$model:generateContent") {
            val apiKey = System.getenv("LITELLM_API_KEY") ?: error("LITELLM_API_KEY environment variable is not set")

            header("x-goog-api-key", apiKey)
            header("Content-Type", "application/json")
            setBody(
                """
                {
                    "contents": [
                        {
                            "parts": [
                                { "text": "$promptMessage" }
                            ]
                        }
                    ]
                }
            """.trimIndent()
            )
        }

        val traces = analyzeSpans()

        assertEquals(1, traces.size)
        val trace = traces.firstOrNull()
        assertNotNull(trace)

        assertEquals(StatusCode.OK, trace.status.statusCode)
        assertEquals("gemini", trace.attributes[AttributeKey.stringKey("gen_ai.system")])
        assertEquals(LITELLM_URL, trace.attributes[AttributeKey.stringKey("gen_ai.api_base")])

        val tracedModel = trace.attributes[AttributeKey.stringKey("gen_ai.response.model")]
        assertEquals(true, tracedModel?.startsWith(model))

        val tracedPrompt =
            Json.parseToJsonElement(trace.attributes[AttributeKey.stringKey("gen_ai.prompt.0.content")]!!).jsonArray[0]
                .jsonObject["text"]?.jsonPrimitive?.content
        assertEquals(promptMessage, tracedPrompt)

        val completionRole = trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.role")]
        val completionContent = trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.content")]

        assertNotNull(completionRole)
        assertTrue(completionRole.isNotEmpty())

        assertNotNull(completionContent)
        assertTrue(completionContent.isNotEmpty())

        // assert that tracing doesn't consume the response body
        val responseBody = response.bodyAsText()
        assertTrue(responseBody.isNotEmpty())

        val responseJson = Json.parseToJsonElement(responseBody).jsonObject

        assertEquals(
            responseJson["responseId"]!!.jsonPrimitive.content,
            trace.attributes[AttributeKey.stringKey("gen_ai.response.id")]
        )
        assertEquals(responseJson["modelVersion"]!!.jsonPrimitive.content, tracedModel)
        assertEquals(
            responseJson["candidates"]?.jsonArray[0]?.jsonObject["content"]?.jsonObject["role"]?.jsonPrimitive?.content,
            completionRole
        )
        assertEquals(
            responseJson["candidates"]?.jsonArray[0]?.jsonObject["content"]?.jsonObject["parts"]?.toString(),
            completionContent
        )
        assertEquals(
            responseJson["usageMetadata"]!!.jsonObject["promptTokenCount"]!!.jsonPrimitive.int,
            trace.attributes[AttributeKey.longKey("gen_ai.usage.input_tokens")]!!.toInt()
        )
        assertEquals(
            responseJson["usageMetadata"]!!.jsonObject["candidatesTokenCount"]!!.jsonPrimitive.int,
            trace.attributes[AttributeKey.longKey("gen_ai.usage.output_tokens")]!!.toInt()
        )
    }

    companion object {
        @Serializable
        private data class Request(
            val messages: List<Message>,
            val model: String,
        )

        @Serializable
        private data class Message(
            val role: String,
            val content: String,
        )

        @JvmStatic
        fun provideTestParameters(): Stream<Arguments> = Stream.of(
            Arguments.of(
                "Request as a string",
                "greet me and introduce yourself",
                "gpt-4o-mini",
                """
                    {
                        "messages": [
                            {
                                "role": "user",
                                "content": "greet me and introduce yourself"
                            }
                        ],
                        "model": "gpt-4o-mini"
                    }
                """.trimIndent()
            ),
            Arguments.of(
                "Request as a Serializable object",
                "Introduce yourself",
                "gpt-4o-mini",
                Request(
                    messages = listOf(
                        Message(role = "user", content = "Introduce yourself")
                    ),
                    model = "gpt-4o-mini"
                )
            ),
        )

        private fun String.unquote(): String {
            if (this.startsWith("\"") && this.endsWith("\"")) {
                return this.substring(1, this.length - 1)
            }
            return this
        }
    }
}