package ai.dev.kit.tracing.fluent.providers

import ai.dev.kit.clients.instrument
import ai.dev.kit.tracing.BaseOpenTelemetryTracingTest
import ai.dev.kit.tracing.LITELLM_URL
import com.google.genai.errors.GenAiIOException
import com.google.genai.types.*
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.StatusCode
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.net.SocketTimeoutException
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import com.google.genai.Client as GeminiClient
import com.google.genai.types.GenerateContentConfig as GeminiGenerateContentConfig
import com.google.genai.types.HttpOptions as GeminiHttpOptions

@Tag("SkipForNonLocal")
class GeminiTracingTest : BaseOpenTelemetryTracingTest() {
    fun createGeminiClient(): GeminiClient {
        val projectId = "jetbrains-grazie"
        val location = "us-central1"
        val apiKey = System.getenv("LITELLM_API_KEY") ?: error("LITELLM_API_KEY environment variable is not set")

        return GeminiClient.builder()
            .vertexAI(true)
            .project(projectId)
            .location(location)
            .httpOptions(
                GeminiHttpOptions.builder()
                    .baseUrl("$LITELLM_URL/vertex_ai")
                    .headers(mapOf("x-litellm-api-key" to "Bearer $apiKey"))
                    .timeout(Duration.ofSeconds(60).toMillis().toInt())
                    .build()
            )
            .build()
    }

    private fun createTool(word: String): Tool {
        return Tool.builder()
            .functionDeclarations(
                FunctionDeclaration.builder()
                    .name(word)
                    .description("Say $word to the user")
                    .parameters(
                        Schema.builder()
                            .type("object")
                            .description("The phrase parameters")
                            .properties(
                                mapOf(
                                    "name" to Schema.builder()
                                        .type("string")
                                        .description("The name of the person to say $word to")
                                        .build()
                                )
                            )
                            .required("name")
                            .build()
                    )
                    .build()
            )
            .build()
    }

    @Test
    fun `test Gemini tool calling auto logging`() = runTest {
        val client = instrument(createGeminiClient())
        val toolName = "hi"
        val greetTool = createTool(toolName)

        val model = "gemini-2.5-flash"
        client.models.generateContent(
            model,
            "Generate polite greeting and introduce yourself. You MUST use the tool named '${toolName}' for greeting!",
            GeminiGenerateContentConfig.builder()
                .temperature(0.8f)
                .tools(greetTool)
                .build()
        )

        val traces = analyzeSpans()

        assertEquals(1, traces.size)
        val trace = traces.firstOrNull()
        assertNotNull(trace)

        // assert request
        assertEquals("hi", trace.attributes[AttributeKey.stringKey("gen_ai.tool.0.function.0.name")])
        assertEquals(
            "Say hi to the user",
            trace.attributes[AttributeKey.stringKey("gen_ai.tool.0.function.0.description")]
        )
        assertEquals("object", trace.attributes[AttributeKey.stringKey("gen_ai.tool.0.function.0.type")])

        // assert response
        assertEquals("hi", trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.tool.0.name")])
        assertTrue(trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.tool.0.arguments")]?.isNotEmpty() == true)
    }

    @Test
    fun `test Gemini tool calling with tool call result`() = runTest {
        val client = instrument(createGeminiClient())
        val greetTool = createTool("hi")

        val model = "gemini-2.5-flash"
        val config = GeminiGenerateContentConfig.builder()
            .temperature(0.0f)
            .tools(greetTool)
            .build()

        // Step 1: Initial user message
        val userMessage = Content.builder()
            .role("user")
            .parts(Part.fromText("Generate greeting via a tool provided to you. Use the name USER. You MUST call the tool exactly once!"))
            .build()

        // Step 2: Get AI response (which should contain a function call)
        val firstResponse = client.models.generateContent(
            model,
            userMessage,
            config,
        )

        // Step 3: Extract function calls and create function responses
        val functionCallResponses = buildList {
            firstResponse.parts()?.forEach { part ->
                part.functionCall().ifPresent { call ->
                    add(
                        Part.fromFunctionResponse(
                            call.name().get(),
                            mapOf("output" to "Hello, my friend!")
                        )
                    )
                }
            }
        }

        // Step 4: Create the conversation history with separate turns
        val conversationHistory = listOf(
            // Turn 1: User message
            userMessage,
            // Turn 2: AI response with function call
            Content.builder()
                .role("model")
                .parts(firstResponse.parts()?.toList() ?: emptyList())
                .build(),
            // Turn 3: Function response
            Content.builder()
                .role("user")
                .parts(functionCallResponses)
                .build()
        )

        // Step 5: final request to AI
        client.models.generateContent(
            model,
            conversationHistory,
            config,
        )

        val traces = analyzeSpans()

        assertEquals(2, traces.size)
        val trace = traces.firstOrNull()
        assertNotNull(trace)

        // assert request
        assertEquals("hi", trace.attributes[AttributeKey.stringKey("gen_ai.tool.0.function.0.name")])
        assertEquals(
            "Say hi to the user",
            trace.attributes[AttributeKey.stringKey("gen_ai.tool.0.function.0.description")]
        )
        assertEquals("object", trace.attributes[AttributeKey.stringKey("gen_ai.tool.0.function.0.type")])

        // assert response
        assertEquals("hi", trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.tool.0.name")])
        assertTrue(trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.tool.0.arguments")]?.isNotEmpty() == true)
    }

    @Test
    fun `test Gemini multiple tools response to tool calls auto tracing`() = runTest {
        val client = instrument(createGeminiClient())
        val greetTool = createTool("hi")
        val goodbyeTool = createTool("goodbye")

        val model = "gemini-2.5-flash"
        val config = GeminiGenerateContentConfig.builder()
            .temperature(0.0f)
            .tools(greetTool, goodbyeTool)
            .build()

        val userMessage = Content.builder()
            .role("user")
            .parts(Part.fromText("Use the provided tools to greet Alex, then say goodbye to him. You MUST use the tools!"))
            .build()

        val firstResponse = client.models.generateContent(
            model,
            userMessage,
            config,
        )

        val functionCallResponses = buildList {
            firstResponse.parts()?.forEach { part ->
                part.functionCall().ifPresent { call ->
                    add(
                        Part.fromFunctionResponse(
                            call.name().get(),
                            mapOf("output" to "ok")
                        )
                    )
                }
            }
        }

        val conversationHistory = listOf(
            userMessage,
            Content.builder().role("model").parts(firstResponse.parts()?.toList() ?: emptyList()).build(),
            Content.builder().role("user").parts(functionCallResponses).build()
        )

        client.models.generateContent(
            model,
            conversationHistory,
            config,
        )

        val traces = analyzeSpans()
        assertEquals(2, traces.size)

        val trace = traces.first()
        // Assert both tools are declared in the request
        assertEquals("hi", trace.attributes[AttributeKey.stringKey("gen_ai.tool.0.function.0.name")])
        assertEquals("goodbye", trace.attributes[AttributeKey.stringKey("gen_ai.tool.1.function.0.name")])

        // If function calls were made, assert both appear in completion metadata
        if ((trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.finish_reason")]
                ?: "").contains("tool_calls") ||
            trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.tool.0.name")] != null
        ) {
            assertTrue(trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.tool.0.name")]?.isNotEmpty() == true)
            assertTrue(trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.tool.1.name")]?.isNotEmpty() == true)
        }
    }

    @Test
    fun `test Gemini auto tracing`() = runTest {
        val model = "gemini-2.5-flash"
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
            trace.attributes[AttributeKey.stringKey("gen_ai.api_base")]
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
                "gemini-2.5-flash",
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
            trace.attributes[AttributeKey.stringKey("gen_ai.api_base")]
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
            trace.attributes[AttributeKey.stringKey("gen_ai.api_base")]
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