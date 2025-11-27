package ai.dev.kit.tracing.fluent.providers

import ai.dev.kit.clients.instrument
import com.google.genai.errors.GenAiIOException
import com.google.genai.types.Content
import com.google.genai.types.GenerateContentResponse
import com.google.genai.types.Part
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.StatusCode
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.net.SocketTimeoutException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import com.google.genai.Client as GeminiClient
import com.google.genai.types.GenerateContentConfig as GeminiGenerateContentConfig


// TODO: fix
// require the provider to be LiteLLM
@EnabledIfEnvironmentVariable(
    named = "LLM_PROVIDER_URL",
    matches = "https://litellm.labs.jb.gg",
    disabledReason = "LLM_PROVIDER_URL environment variable is not https://litellm.labs.jb.gg",
)
@Tag("gemini")
class GeminiTracingTest : BaseGeminiTracingTest() {
    @Test
    fun `test nested instrumentation calls don't cause duplicative tracing`() = runTest {
        val client = instrument(instrument(instrument(createGeminiClient())))

        val model = "gemini-2.5-flash"
        client.models.generateContent(
            model,
            "Say hi!",
            GeminiGenerateContentConfig.builder()
                .temperature(0.0f)
                .build()
        )

        val traces = analyzeSpans()
        assertEquals(1, traces.size)
    }

    @Test
    fun `test Gemini tool calling auto logging`() = runTest {
        val client = instrument(createGeminiClient())

        val toolName = "hi"
        val greetTool = createTool(toolName)

        val model = "gemini-2.5-flash"
        val response = client.models.generateContent(
            model,
            "Call the `hi` tool with the argument `name` set to 'USER'. Do not output any conversational text; only execute the tool call.",
            GeminiGenerateContentConfig.builder()
                .temperature(0.0f)
                .tools(greetTool)
                .build()
        )

        flushTracesAndAssumeToolCalled(response, toolName, GenerateContentResponse::containsToolCall)

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
        assertFalse(trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.tool.0.arguments")].isNullOrEmpty())
    }

    @Test
    fun `test Gemini tool calling with tool call result`() = runTest {
        val client = instrument(createGeminiClient())

        val toolName = "hi"
        val greetTool = createTool(toolName)

        val model = "gemini-2.5-flash"
        val config = GeminiGenerateContentConfig.builder()
            .temperature(0.0f)
            .tools(greetTool)
            .build()

        // Step 1: Initial user message
        val userMessage = Content.builder()
            .role("user")
            .parts(Part.fromText("Call the `hi` tool with the argument `name` set to 'USER'. Do not output any conversational text; only execute the tool call."))
            .build()

        // Step 2: Get AI response (which should contain a function call)
        val firstResponse = client.models.generateContent(
            model,
            userMessage,
            config,
        )

        flushTracesAndAssumeToolCalled(firstResponse, toolName, GenerateContentResponse::containsToolCall)

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
        assertFalse(trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.tool.0.arguments")].isNullOrEmpty())
    }

    @Test
    fun `test Gemini multiple tools response to tool calls auto tracing`() = runTest {
        val client = instrument(createGeminiClient())

        val greetToolName = "hi"
        val goodbyeToolName = "goodbye"

        val greetTool = createTool("hi")
        val goodbyeTool = createTool("goodbye")

        val model = "gemini-2.5-flash"
        val config = GeminiGenerateContentConfig.builder()
            .temperature(0.0f)
            .tools(greetTool, goodbyeTool)
            .build()

        val userMessage = Content.builder()
            .role("user")
            .parts(Part.fromText("Call the `hi` tool with the argument `name` set to 'USER' and `goodbye` with the argument `name` set to 'USER'. Do not output any conversational text; only execute the tool calls."))
            .build()

        val firstResponse = client.models.generateContent(
            model,
            userMessage,
            config,
        )

        flushTracesAndAssumeToolCalled(firstResponse, greetToolName, GenerateContentResponse::containsToolCall)
        flushTracesAndAssumeToolCalled(firstResponse, goodbyeToolName, GenerateContentResponse::containsToolCall)

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
            assertFalse(trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.tool.0.name")].isNullOrEmpty())
            assertFalse(trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.tool.1.name")].isNullOrEmpty())
        }
    }

    @Test
    fun `test Gemini auto tracing`() = runTest {
        val client = instrument(createGeminiClient())

        val model = "gemini-2.5-flash"

        client.models.generateContent(
            model,
            "Generate polite greeting and introduce yourself",
            GeminiGenerateContentConfig.builder()
                .temperature(0.0f)
                .build()
        )

        val traces = analyzeSpans()

        assertEquals(1, traces.size)
        val trace = traces.firstOrNull()
        assertNotNull(trace)

        assertEquals(StatusCode.OK, trace.status.statusCode)
        assertEquals(
            llmProviderUrl,
            trace.attributes[AttributeKey.stringKey("gen_ai.api_base")]
        )
        val responseModel = trace.attributes[AttributeKey.stringKey("gen_ai.response.model")]
        assertNotNull(responseModel)
        assertTrue(responseModel.startsWith(model))

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
                    .temperature(0.0f)
                    .build()
            )
        } catch (_: GenAiIOException) {
            // suppress
        }

        val traces = analyzeSpans()
        assertTrue(traces.isNotEmpty())
        val trace = traces.first()

        assertEquals(StatusCode.ERROR, trace.status.statusCode)
        assertEquals(
            llmProviderUrl,
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
                    .temperature(0.0f)
                    .build()
            )
        } catch (_: Exception) {
            // suppress
        }

        val traces = analyzeSpans()

        assertEquals(1, traces.size)
        val trace = traces.firstOrNull()
        assertNotNull(trace)

        assertEquals(StatusCode.ERROR, trace.status.statusCode)
        assertEquals(
            llmProviderUrl,
            trace.attributes[AttributeKey.stringKey("gen_ai.api_base")]
        )

        assertFalse(trace.attributes[AttributeKey.stringKey("gen_ai.error.message")].isNullOrEmpty())
        assertFalse(trace.attributes[AttributeKey.stringKey("gen_ai.error.code")].isNullOrEmpty())
    }

    @Test
    fun `test Gemini additional attributes`() = runTest {
        val model = "gemini-2.5-flash"
        val client = instrument(createGeminiClient())

        client.models.generateContent(
            model,
            "Generate polite greeting and introduce yourself",
            GeminiGenerateContentConfig.builder()
                // "labels" attribute is not mapped in the API handler
                .labels(mapOf("labelKey" to "labelValue"))
                .temperature(0.0f)
                .build()
        )

        val traces = analyzeSpans()

        assertEquals(1, traces.size)
        val trace = traces.firstOrNull()
        assertNotNull(trace)
        val labelsAttribute = trace.attributes[AttributeKey.stringKey("tracy.request.labels")]

        assertNotNull(labelsAttribute)
        assertEquals("{\"labelKey\":\"labelValue\"}", labelsAttribute)
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

private fun GenerateContentResponse.containsToolCall(toolName: String): Boolean {
    return parts()?.any { part ->
        part.functionCall()
            .map { call -> call.name().orElse(null) == toolName }
            .orElse(false)
    } ?: false
}