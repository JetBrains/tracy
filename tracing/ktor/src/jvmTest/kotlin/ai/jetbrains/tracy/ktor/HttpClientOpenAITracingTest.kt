package ai.jetbrains.tracy.ktor

import ai.jetbrains.tracy.core.http.parsers.MultipartFormDataParser
import ai.jetbrains.tracy.core.tracing.TracingManager
import ai.jetbrains.tracy.openai.adapters.OpenAILLMTracingAdapter
import ai.jetbrains.tracy.test.utils.BaseAITracingTest
import ai.jetbrains.tracy.test.utils.MediaContentAttributeValues
import ai.jetbrains.tracy.test.utils.MediaSource
import ai.jetbrains.tracy.test.utils.toMediaContentAttributeValues
import com.openai.core.ClientOptions.Companion.PRODUCTION_URL
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_MODEL
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_TEMPERATURE
import kotlinx.coroutines.test.runTest
import kotlinx.io.readByteArray
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

@Tag("openai")
class HttpClientOpenAITracingTest : BaseAITracingTest() {
    private val llmTracingAdapter = OpenAILLMTracingAdapter()

    @ParameterizedTest
    @MethodSource("provideTestParameters")
    fun `test Ktor HttpClient auto tracing with different request body types for OpenAI`(
        @Suppress("UNUSED_PARAMETER")
        testName: String,
        prompt: String,
        model: String,
        requestBody: Any,
    ) = runTest {
        val client: HttpClient = instrument(HttpClient {
            install(ContentNegotiation) {
                json(Json { prettyPrint = true })
            }
        }, llmTracingAdapter)

        val response = client.post("$baseUrl/v1/chat/completions") {
            addAuthHeaders()
            when (requestBody) {
                // for the request.bodyType to be set correctly
                is Request -> setBody<Request>(requestBody)
                is String -> setBody<String>(requestBody)
                else -> setBody(requestBody)
            }
        }

        val traces = analyzeSpans()
        assertTracesCount(1, traces)
        val trace = traces.first()

        assertEquals(StatusCode.OK, trace.status.statusCode)

        assertEquals("openai", trace.attributes[AttributeKey.stringKey("gen_ai.system")])
        assertEquals(baseUrl, trace.attributes[AttributeKey.stringKey("gen_ai.api_base")])

        val tracedModel = trace.attributes[AttributeKey.stringKey("gen_ai.response.model")]
        assertNotNull(tracedModel)
        assertTrue(tracedModel.startsWith(model))

        assertEquals("user", trace.attributes[AttributeKey.stringKey("gen_ai.prompt.0.role")])
        assertEquals(prompt, trace.attributes[AttributeKey.stringKey("gen_ai.prompt.0.content")]?.unquote())

        val completionRole = trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.role")]
        val completionContent = trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.content")]

        assertFalse(completionRole.isNullOrEmpty())
        assertFalse(completionContent.isNullOrEmpty())

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
        val client: HttpClient = instrument(HttpClient(), adapter = llmTracingAdapter)

        val model = "gpt-4o-mini"
        val response = client.post("$baseUrl/v1/chat/completions") {
            addAuthHeaders(acceptStream = true)
            setBody(
                """
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
            """.trimIndent()
            )
        }

        //consume the response
        response.bodyAsChannel()

        val traces = analyzeSpans()
        assertTracesCount(1, traces)
        val trace = traces.first()

        val content = trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.content")]
        assertFalse(content.isNullOrEmpty())
    }

    @Test
    fun `test Ktor HttpClient auto tracing streaming does not trace if disabled`() = runTest {
        TracingManager.isTracingEnabled = false
        val client: HttpClient = instrument(HttpClient(), adapter = llmTracingAdapter)

        val response = client.post("$baseUrl/v1/chat/completions") {
            addAuthHeaders(acceptStream = true)
            setBody(
                """
            {
                "messages": [
                    { "role": "user", "content": "hello world" }
                ],
                "model": "gpt-4o-mini",
                "stream": true
            }
            """.trimIndent()
            )
        }

        // Enable tracing after the request started (mid-flight)
        TracingManager.isTracingEnabled = true

        val responseText = response.bodyAsChannel().readRemaining().readText()
        assertTrue(responseText.isNotEmpty())

        val traces = analyzeSpans()
        assertTrue(traces.isEmpty(), "No spans should be created for requests started with tracing disabled")
    }

    @Test
    fun `test Ktor HttpClient auto tracing does not trace if disabled`() = runTest {
        TracingManager.isTracingEnabled = false
        val client = instrument(HttpClient(), adapter = llmTracingAdapter)

        val response = client.post("$baseUrl/v1/chat/completions") {
            addAuthHeaders(acceptStream = true)
            setBody(
                """
                {
                    "messages": [
                        {
                            "role": "user",
                            "content": "hello world"
                        }
                    ],
                    "model": "gpt-4o-mini",
                    "stream": false
                }
                """.trimIndent()
            )
        }

        // Enable tracing after the request started (mid-flight)
        TracingManager.isTracingEnabled = true

        val responseText = response.bodyAsChannel().readRemaining().readText()
        assertTrue(responseText.isNotEmpty())

        val traces = analyzeSpans()
        assertTrue(traces.isEmpty(), "No spans should be created for requests started with tracing disabled")
    }

    @Test
    fun `test Ktor HttpClient auto tracing for Bad Request in OpenAI`() = runTest {
        val mockedClient = HttpClient(MockEngine) {
            engine {
                addHandler { _ ->
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

        val client: HttpClient = instrument(mockedClient, llmTracingAdapter)

        val response = client.post("$baseUrl/v1/chat/completions") {
            addAuthHeaders()
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
        assertTracesCount(1, traces)
        val trace = traces.first()

        assertEquals(StatusCode.ERROR, trace.status.statusCode)
        assertEquals(baseUrl, trace.attributes[AttributeKey.stringKey("gen_ai.api_base")])

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

        val client: HttpClient = instrument(mockedClient, llmTracingAdapter)

        client.post("$baseUrl/v1/chat/completions") {
            addAuthHeaders()
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
        assertTracesCount(1, traces)
        val trace = traces.first()

        assertEquals(StatusCode.OK, trace.status.statusCode)
        assertEquals(baseUrl, trace.attributes[AttributeKey.stringKey("gen_ai.api_base")])
        assertEquals("null", trace.attributes[AttributeKey.stringKey("gen_ai.prompt.0.role")])
        assertEquals("null", trace.attributes[AttributeKey.stringKey("gen_ai.prompt.0.content")])
        assertEquals(null, trace.attributes[GEN_AI_REQUEST_TEMPERATURE])
        assertEquals("null", trace.attributes[GEN_AI_REQUEST_MODEL])
        assertEquals("null", trace.attributes[AttributeKey.stringKey("gen_ai.response.model")])
        assertEquals("null", trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.role")])
        assertEquals("null", trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.content")])
        assertEquals("null", trace.attributes[AttributeKey.stringKey("gen_ai.response.id")])
    }

    @ParameterizedTest
    @MethodSource("provideOpenAIBodies")
    fun `test tracing for OpenAI doesn't fail when tools are null`(
        @Suppress("UNUSED_PARAMETER")
        testName: String,
        endpoint: String,
        requestBody: String,
    ) = runTest(timeout = 3.minutes) {
        val client: HttpClient = instrument(HttpClient(), llmTracingAdapter)

        val response = client.post(endpoint) {
            timeout {
                requestTimeoutMillis = 3.minutes.inWholeMilliseconds
            }
            addAuthHeaders()
            setBody(requestBody)
        }

        val traces = analyzeSpans()
        assertTracesCount(1, traces)
        val trace = traces.first()

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(StatusCode.OK, trace.status.statusCode)
        assertEquals(baseUrl, trace.attributes[AttributeKey.stringKey("gen_ai.api_base")])

        assertFalse(trace.attributes[AttributeKey.stringKey("gen_ai.response.model")].isNullOrEmpty())
        assertEquals(null, trace.attributes[GEN_AI_REQUEST_TEMPERATURE])

        assertEquals(body["id"]!!.jsonPrimitive.content, trace.attributes[AttributeKey.stringKey("gen_ai.response.id")])
        assertFalse(trace.attributes[AttributeKey.stringKey("gen_ai.prompt.0.role")].isNullOrEmpty())
        assertFalse(trace.attributes[AttributeKey.stringKey("gen_ai.prompt.0.content")].isNullOrEmpty())
        assertFalse(trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.role")].isNullOrEmpty())
        assertFalse(trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.content")].isNullOrEmpty())
    }

    @Test
    fun `test streaming requests`() = runTest {
        val client = instrument(HttpClient(), adapter = llmTracingAdapter)

        val model = "gpt-4o-mini"

        val firstRequest = "first request"
        val secondRequest = "second request"

        val resp1 = client.postChatCompletion(model, firstRequest, acceptStream = true)
        val resp2 = client.postChatCompletion(model, secondRequest, acceptStream = true)

        consumeResponses(resp1, resp2)

        validateTracesContent(listOf(firstRequest, secondRequest))
    }

    @Test
    fun `test non-streaming requests`() = runTest {
        val client = instrument(HttpClient(), adapter = llmTracingAdapter)

        val model = "gpt-4o-mini"

        val firstRequest = "first request"
        val secondRequest = "second request"

        val resp1 = client.postChatCompletion(model, firstRequest)
        val resp2 = client.postChatCompletion(model, secondRequest)

        consumeResponses(resp1, resp2)

        validateTracesContent(listOf(firstRequest, secondRequest))
    }

    @Test
    fun `test mixed stream and non-stream requests`() = runTest {
        val client = instrument(HttpClient(), adapter = llmTracingAdapter)

        val model = "gpt-4o-mini"

        val firstRequest = "first request"
        val secondRequest = "second request"

        val resp1 = client.postChatCompletion(model, firstRequest) // regular
        val resp2 = client.postChatCompletion(model, secondRequest, acceptStream = true)

        consumeResponses(resp1, resp2)

        validateTracesContent(listOf(firstRequest, secondRequest))
    }

    @Test
    fun `test Ktor's form data content body gets parsed by form data parser`() = runTest {
        val model = "gpt-image-1"
        val prompt = "Remove all dogs from the image"

        val image = MediaSource.File("cat-n-dog-2-alpha.png", "image/png")
        val filename = image.filepath.substringAfterLast("/")
        val imageBytes = readResource(image.filepath).readBytes()

        val body = MultiPartFormDataContent(
            formData {
                val plainText = ContentType.Text.Plain.toString()

                append("model", model, Headers.build {
                    append(HttpHeaders.ContentType, plainText)
                })
                append("prompt", prompt, Headers.build {
                    append(HttpHeaders.ContentType, plainText)
                })
                // image
                append("image", imageBytes, Headers.build {
                    append(HttpHeaders.ContentType, image.contentType)
                    append(
                        HttpHeaders.ContentDisposition,
                        "filename=\"${filename}\""
                    )
                })
            }
        )

        val ch = ByteChannel()
        body.writeTo(ch)
        val bytes = ch.readRemaining().readByteArray()

        val parser = MultipartFormDataParser()
        val data = parser.parse(body.contentType, bytes)

        assertEquals(3, data.parts.size,
            "Expected 3 parts in the parsed multipart form data: 1) model, 2) prompt, and 3) image")

        val modelPart = data.parts.first { it.name == "model" }
        val promptPart = data.parts.first { it.name == "prompt" }
        val imagePart = data.parts.first { it.name == "image" }

        assertEquals(model, modelPart.content.toString(Charsets.UTF_8),
            "Model names don't match")
        assertEquals(prompt, promptPart.content.toString(Charsets.UTF_8),
            "Prompts don't match")
        // image assertions
        assertEquals(
            imageBytes.toString(Charsets.UTF_8),
            imagePart.content.toString(Charsets.UTF_8),
            "Image contents don't match"
        )
        assertEquals(image.contentType, imagePart.contentType?.toString(),
            "Image content types don't match")
        assertEquals(filename, imagePart.filename, "Filenames don't match")
    }

    @Test
    fun `test image edits endpoint with multipart form data gets traced`() = runTest(timeout = 3.minutes) {
        val client = instrument(HttpClient {
            install(io.ktor.client.plugins.HttpTimeout) {
                requestTimeoutMillis = 3.minutes.inWholeMilliseconds
            }
        }, adapter = llmTracingAdapter)

        val model = "gpt-image-1"
        val prompt = "Remove all dogs from the image"
        val image = MediaSource.File("cat-n-dog-2-alpha.png", "image/png")

        val response = client.post("$baseUrl/v1/images/edits") {
            val body = MultiPartFormDataContent(
                formData {
                    val plainText = ContentType.Text.Plain.toString()

                    append("model", model, Headers.build {
                        append(HttpHeaders.ContentType, plainText)
                    })
                    append("prompt", prompt, Headers.build {
                        append(HttpHeaders.ContentType, plainText)
                    })
                    // image
                    append("image", readResource(image.filepath).readBytes(), Headers.build {
                        append(HttpHeaders.ContentType, image.contentType)
                        append(
                            HttpHeaders.ContentDisposition,
                            "filename=\"${image.filepath.substringAfterLast("/")}\""
                        )
                    })
                }
            )
            val contentType = ContentType.MultiPart.FormData.withParameter("boundary", body.boundary)

            header("Authorization", "Bearer $llmProviderApiKey")
            contentType(contentType)
            setBody(body)
        }

        val traces = analyzeSpans()
        assertTracesCount(1, traces)
        val trace = traces.first()

        for (attr in trace.attributes.asMap()) {
            println("${attr.key}=${attr.value.toString().substring(0, 500.coerceAtMost(attr.value.toString().length))}")
        }

        val tracedPrompt = trace.attributes[AttributeKey.stringKey("gen_ai.prompt.0.content")]
        assertEquals(prompt, tracedPrompt)

        val tracedModel = trace.attributes[AttributeKey.stringKey("gen_ai.request.model")]
        assertEquals(model, tracedModel)

        val responseImageData = Json.parseToJsonElement(response.bodyAsText())
            .jsonObject["data"]!!.jsonArray[0].jsonObject["b64_json"]!!.jsonPrimitive.content

        val expected = MediaContentAttributeValues.Data(
            field = "output",
            contentType = "image/png",
            data = responseImageData,
        )

        verifyMediaContentUploadAttributes(trace, expected = listOf(
            image.toMediaContentAttributeValues("input"),
            expected,
        ))
    }

    private suspend fun HttpClient.postChatCompletion(
        model: String,
        userRequest: String,
        acceptStream: Boolean = false
    ): HttpResponse {
        return post("$baseUrl/v1/chat/completions") {
            addAuthHeaders(acceptStream = acceptStream)
            setBody(
                """
            {
                "messages": [
                    { "role": "user", "content": "$userRequest" }
                ],
                "model": "$model",
                "stream": true
            }
            """.trimIndent()
            )
        }
    }

    private suspend fun consumeResponses(vararg responses: HttpResponse) {
        responses.forEach { it.bodyAsChannel() }
    }

    private fun validateTracesContent(expectedPrompts: List<String>) {
        val traces = analyzeSpans()
        assertEquals(expectedPrompts.size, traces.size)
        expectedPrompts.zip(traces).forEach { (expected, trace) ->
            assertEquals(
                expected,
                trace.attributes[AttributeKey.stringKey("gen_ai.prompt.0.content")]
            )
        }
    }

    private fun HttpRequestBuilder.addAuthHeaders(acceptStream: Boolean = false) {
        header("Authorization", "Bearer $llmProviderApiKey")
        header("Content-Type", "application/json")
        if (acceptStream) header("Accept", "text/event-stream")
    }

    companion object {
        private val llmProviderUrl: String = System.getenv("LLM_PROVIDER_URL") ?: PRODUCTION_URL
        private val llmProviderApiKey =
            System.getenv("OPENAI_API_KEY") ?: System.getenv("LLM_PROVIDER_API_KEY")
            ?: error("LLM_PROVIDER_API_KEY environment variable is not set")

        // llmProviderUrl = https://api.openai.com/v1, gen_ai.api_base = https://api.api.openai.com
        private val baseUrl = llmProviderUrl.let {
            if (it.endsWith("/v1")) it.removeSuffix("/v1") else it
        }

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

        @JvmStatic
        fun provideOpenAIBodies(): Stream<Arguments> = Stream.of(
            Arguments.of(
                "Completions API",
                "$llmProviderUrl/chat/completions",
                """
                    {
                        "model": "gpt-4.1-mini-2025-04-14",
                        "messages": [
                            {
                                "role": "user",
                                "content": "You are a programming task description summarizer",
                                "tool_calls": null,
                                "tool_call_id": null,
                                "logits": null
                            }
                        ],
                        "tools": null,
                        "tool_choice": null,
                        "temperature": null,
                        "top_p": null,
                        "n": null,
                        "stream": false,
                        "stop": null,
                        "max_tokens": null,
                        "max_completion_tokens": null,
                        "presence_penalty": null,
                        "frequency_penalty": null,
                        "logit_bias": null,
                        "user": null,
                        "seed": 100000
                    }
                """.trimIndent()
            ),
            Arguments.of(
                "Responses API",
                "$llmProviderUrl/responses",
                """
                    {
                        "model": "gpt-4",
                        "input": [
                            {
                                "role": "user",
                                "content": "say only the word 'hello' in response."
                            }
                        ],
                        "tools": null,
                        "temperature": null,
                        "top_p": null,
                        "parallel_tool_calls": false,
                        "stream": false,
                        "tool_choice": null
                    }
                """.trimIndent()
            ),
        )
    }
}

internal fun String.unquote(): String {
    if (this.startsWith("\"") && this.endsWith("\"")) {
        return this.substring(1, this.length - 1)
    }
    return this
}