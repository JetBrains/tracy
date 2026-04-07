/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.openai.adapters

import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.core.ClientOptions.Companion.PRODUCTION_URL
import com.openai.core.JsonArray
import com.openai.core.JsonString
import com.openai.core.JsonValue
import com.openai.models.ChatModel
import com.openai.models.FunctionDefinition
import com.openai.models.FunctionParameters
import com.openai.models.chat.completions.ChatCompletion
import com.openai.models.chat.completions.ChatCompletionFunctionTool
import com.openai.models.chat.completions.ChatCompletionMessageToolCall
import com.openai.models.chat.completions.ChatCompletionTool
import com.openai.models.responses.FunctionTool
import com.openai.models.responses.Response
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.StatusCode
import mu.KotlinLogging
import org.jetbrains.ai.tracy.core.interceptors.patchOpenAICompatibleClient
import org.jetbrains.ai.tracy.test.utils.BaseAITracingTest
import org.jetbrains.ai.tracy.test.utils.fixtures.*
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.TestInstance
import java.io.File
import java.nio.file.Path
import java.time.Duration
import kotlin.jvm.optionals.getOrNull
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val logger = KotlinLogging.logger {}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseOpenAITracingTest : BaseAITracingTest() {
    protected val testMode = TestMode.current()

    private var mockServer: FixtureMockServer? = null

    /**
     * Current test name, captured automatically for fixture naming.
     * Can be overridden by providing explicit `fixtureTag` to [createOpenAIClient].
     */
    protected var currentTestName: String? = null

    /**
     * The name of a test suite whose test cas is currently running.
     * It is used for folder naming when recording fixtures locally.
     */
    private var currentContainingTestSuiteName: String? = null

    @BeforeEach
    fun captureTestName(testInfo: TestInfo) {
        currentContainingTestSuiteName = testInfo.testClass.getOrNull()?.simpleName
        currentTestName = createFixtureTag(testInfo)
    }

    protected val llmProviderApiKey: String
        get() = when (testMode) {
            TestMode.MOCK -> "mock-api-key"
            TestMode.RECORD -> System.getenv("OPENAI_API_KEY") ?: System.getenv("LLM_PROVIDER_API_KEY")
                ?: error("Neither OPENAI_API_KEY nor LLM_PROVIDER_API_KEY environment variables are set")
        }

    /**
     * When no value is provided, defaults to [PRODUCTION_URL].
     *
     * When LiteLLM is used as a provider, prefer [patchedProviderUrl].
     */
    protected val llmProviderUrl: String
        get() = when (testMode) {
            TestMode.MOCK -> mockServer?.url() ?: error("Mock server not initialized")
            TestMode.RECORD -> System.getenv("LLM_PROVIDER_URL") ?: PRODUCTION_URL
        }

    /**
     * When LiteLLM is used as a provider, the API URL gets changed to the
     * OpenAI-oriented pass-through endpoint.
     *
     * See [LiteLLM: Create Pass Through Endpoints](https://docs.litellm.ai/docs/proxy/pass_through)
     */
    protected val patchedProviderUrl: String
        get() = when (testMode) {
            TestMode.MOCK -> llmProviderUrl
            TestMode.RECORD -> when (val baseUrl = llmProviderUrl.removeSuffix("/v1")) {
                // TODO: remove direct use of litellm
                // when using LiteLLM, switch to the pass-through
                "https://litellm.labs.jb.gg" -> "$baseUrl/openai"
                else -> llmProviderUrl
            }
        }

    @BeforeAll
    fun setupFixturesAndStartMockServer() {
        when (testMode) {
            TestMode.MOCK -> {
                val fixturesDir = getFixturesDirectory()
                mockServer = FixtureMockServer(fixturesDir).apply { start() }
                logger.info { "Test mode: MOCK - Using fixtures from $fixturesDir" }
                logger.info { "Mock server started at ${mockServer?.url()}" }
            }
            TestMode.RECORD -> {
                logger.info { "Test mode: RECORD - Will record responses to fixtures" }
            }
        }
    }

    @AfterAll
    fun tearDownFixtures() {
        mockServer?.stop()
    }

    protected fun isMockMode() = testMode == TestMode.MOCK

    protected open fun createOpenAIClient(
        url: String? = llmProviderUrl,
        apiKey: String = llmProviderApiKey,
        timeout: Duration = Duration.ofSeconds(60),
        fixtureTag: String? = null
    ): OpenAIClient {
        // Determine the fixture identifier: explicit tag > current test name
        val fixtureIdentifier = fixtureTag ?: currentTestName!!
        val testSuiteName = currentContainingTestSuiteName ?: "unknown"

        val builder = OpenAIOkHttpClient.builder()
            .baseUrl(url)
            .apiKey(apiKey)
            .timeout(timeout)

        // In MOCK mode, add a fixture tag and test suite name headers,
        // so the mock server can match the right fixture
        if (testMode == TestMode.MOCK) {
            builder.putHeader(FIXTURE_TEST_SUITE_NAME_HEADER, testSuiteName)
            builder.putHeader(FIXTURE_TAG_HEADER, fixtureIdentifier)
        }

        val client = builder.build()

        // add a recording interceptor in RECORD mode
        if (testMode == TestMode.RECORD) {
            val fixturesDir = getFixturesDirectory()
            val recordingInterceptor = RecordingInterceptor(
                fixturesDir = fixturesDir,
                sanitizer = OpenAISanitizer(),
                containingTestSuiteName = testSuiteName,
                fixtureTag = fixtureIdentifier,
            )
            patchOpenAICompatibleClient(client, recordingInterceptor)
        }

        return client
    }

    private fun getFixturesDirectory(): Path {
        // Get the test resources directory
        val resourcesDir = File("src/jvmTest/resources").absoluteFile
        val fixturesDir = resourcesDir.resolve("fixtures")
        return fixturesDir.toPath()
    }

    protected fun validateBasicTracing(model: ChatModel) {
        validateBasicTracing(llmProviderUrl, model.asString())
    }

    protected fun validateErrorStatus() {
        val traces = analyzeSpans()
        val trace = traces.firstOrNull()
        assertNotNull(trace)

        assertEquals(StatusCode.ERROR, trace.status.statusCode)
        assertTrue(llmProviderUrl.startsWith(trace.attributes[AttributeKey.stringKey("gen_ai.api_base")].toString()))

        assertFalse(trace.attributes[AttributeKey.stringKey("gen_ai.error.message")].isNullOrEmpty())
        assertFalse(trace.attributes[AttributeKey.stringKey("gen_ai.error.code")].isNullOrEmpty())
    }

    protected fun validateToolCall() {
        val traces = analyzeSpans()
        assertTracesCount(1, traces)
        val trace = traces.first()

        assertEquals("hi", trace.attributes[AttributeKey.stringKey("gen_ai.tool.0.name")])
        assertEquals("function", trace.attributes[AttributeKey.stringKey("gen_ai.tool.0.type")])
        assertFalse(trace.attributes[AttributeKey.stringKey("gen_ai.tool.0.description")].isNullOrEmpty())
        assertFalse(trace.attributes[AttributeKey.stringKey("gen_ai.tool.0.parameters")].isNullOrEmpty())

        // if AI called the tool when check its props
        if (trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.finish_reason")] == "tool_calls") {
            assertFalse(trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.tool.0.name")].isNullOrEmpty())
            assertFalse(trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.tool.0.call.id")].isNullOrEmpty())
            assertFalse(trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.tool.0.call.type")].isNullOrEmpty())
            assertFalse(trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.tool.0.arguments")].isNullOrEmpty())
        }
    }

    protected fun validateToolCallResponse() {
        val traces = analyzeSpans()
        assertTracesCount(2, traces)

        // contains AI's request for a tool call
        val toolCallRequestTrace = traces.firstOrNull()
        // contains an answer to a tool call
        val toolCallResponseTrace = traces.lastOrNull()
        assertNotNull(toolCallRequestTrace)
        assertNotNull(toolCallResponseTrace)

        assertEquals("hi", toolCallRequestTrace.attributes[AttributeKey.stringKey("gen_ai.tool.0.name")])
        assertEquals("function", toolCallRequestTrace.attributes[AttributeKey.stringKey("gen_ai.tool.0.type")])

        // if AI called the tool, then check its props
        if (toolCallRequestTrace.attributes[AttributeKey.stringKey("gen_ai.completion.0.finish_reason")] == "tool_calls") {
            assertEquals("tool", toolCallResponseTrace.attributes[AttributeKey.stringKey("gen_ai.prompt.2.role")])
            assertFalse(toolCallResponseTrace.attributes[AttributeKey.stringKey("gen_ai.prompt.2.content")].isNullOrEmpty())
            assertFalse(toolCallResponseTrace.attributes[AttributeKey.stringKey("gen_ai.prompt.2.tool_call_id")].isNullOrEmpty())
        }
    }

    protected fun validateMultipleToolCallResponseWithInput() {
        val traces = analyzeSpans()
        assertTracesCount(2, traces)

        val toolCallRequestTrace = traces.first()
        val toolCallResponseTrace = traces.last()

        assertEquals("hi", toolCallRequestTrace.attributes[AttributeKey.stringKey("gen_ai.tool.0.name")])
        assertEquals("goodbye", toolCallRequestTrace.attributes[AttributeKey.stringKey("gen_ai.tool.1.name")])

        if (toolCallRequestTrace.attributes[AttributeKey.stringKey("gen_ai.completion.0.finish_reason")] == "tool_calls") {
            assertFalse(toolCallRequestTrace.attributes[AttributeKey.stringKey("gen_ai.completion.0.tool.0.name")].isNullOrEmpty())
            assertFalse(toolCallRequestTrace.attributes[AttributeKey.stringKey("gen_ai.completion.0.tool.1.name")].isNullOrEmpty())

            assertEquals("tool", toolCallResponseTrace.attributes[AttributeKey.stringKey("gen_ai.prompt.2.role")])
            assertFalse(toolCallResponseTrace.attributes[AttributeKey.stringKey("gen_ai.prompt.2.content")].isNullOrEmpty())
            assertFalse(toolCallResponseTrace.attributes[AttributeKey.stringKey("gen_ai.prompt.2.tool_call_id")].isNullOrEmpty())

            assertEquals("tool", toolCallResponseTrace.attributes[AttributeKey.stringKey("gen_ai.prompt.3.role")])
            assertFalse(toolCallResponseTrace.attributes[AttributeKey.stringKey("gen_ai.prompt.3.content")].isNullOrEmpty())
            assertFalse(toolCallResponseTrace.attributes[AttributeKey.stringKey("gen_ai.prompt.3.tool_call_id")].isNullOrEmpty())
        }
    }

    protected fun validateAdditionalAttributes() {
        val traces = analyzeSpans()
        val trace = traces.firstOrNull()

        assertEquals(
            "{\"metadataKey\":\"metadataValue\"}",
            trace?.attributes?.get(AttributeKey.stringKey("tracy.request.metadata"))
        )
        assertEquals(
            "\"additionalBodyPropertyValue\"",
            trace?.attributes?.get(AttributeKey.stringKey("tracy.request.additionalBodyPropertyKey"))
        )
    }

    protected fun createChatCompletionTool(word: String): ChatCompletionTool {
        val functionTool = ChatCompletionFunctionTool.builder()
            .type(JsonString.of("function"))
            .function(
                FunctionDefinition.builder()
                    .description("Say $word to the user")
                    .name(word)
                    .parameters(
                        FunctionParameters.builder()
                            .putAdditionalProperty("type", JsonValue.from("object"))
                            .putAdditionalProperty(
                                "properties",
                                JsonValue.from(mapOf("name" to mapOf("type" to "string")))
                            )
                            .putAdditionalProperty("required", JsonArray.of(listOf(JsonString.of("name"))))
                            .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                            .build()
                    )
                    .build()
            )
            .build()

        return ChatCompletionTool.ofFunction(functionTool)
    }

    protected fun createFunctionTool(word: String): FunctionTool {
        val schema = JsonValue.from(
            mapOf(
                "type" to "object",
                "properties" to mapOf("name" to mapOf("type" to "string")),
                "required" to listOf("name"),
                "additionalProperties" to false
            )
        )
        return FunctionTool.builder()
            .name(word)
            .description("Say $word to the user")
            .parameters(schema)
            .strict(false)
            .build()
    }

    protected fun validateStreaming(output: String) {
        val traces = analyzeSpans()
        assertTracesCount(1, traces)
        val trace = traces.first()

        val contentType = trace.attributes[AttributeKey.stringKey("gen_ai.completion.content.type")]
        assertNotNull(contentType, "Missing gen_ai.completion.content.type attribute")
        assertTrue(contentType.startsWith("text/event-stream"))

        assertFalse(trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.content")].isNullOrEmpty())
        assertEquals(output, trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.content")])
    }

    protected val ChatCompletionMessageToolCall.id: String
        get() {
            val toolCall = this
            val id = if (toolCall.isFunction()) {
                toolCall.function().get().id()
            } else if (toolCall.isCustom()) {
                toolCall.custom().get().id()
            } else {
                throw IllegalStateException("Cannot extract ID of the tool call $toolCall")
            }
            return id
        }

    /**
     * Assumes that the provided URL points to the expected OpenAI production endpoint.
     * Uses JUnit assumptions to enforce this condition during testing.
     *
     * @param url The URL to be validated as an OpenAI production endpoint.
     */
    protected fun assumeOpenAIEndpointOrMockMode(url: String) {
        Assumptions.assumeTrue(isMockMode() || url.startsWith(PRODUCTION_URL))
    }
}

internal val ChatCompletionMessageToolCall.name: String
    get() {
        val toolCall = this
        val name = if (toolCall.isFunction()) {
            toolCall.function().get().function().name()
        } else if (toolCall.isCustom()) {
            toolCall.custom().get().custom().name()
        } else {
            throw IllegalStateException("Cannot extract name of the tool call $toolCall")
        }
        return name
    }

internal fun Response.containsToolCall(toolName: String): Boolean {
    return output().any { item ->
        item.functionCall()
            .map { call -> call.name() == toolName }
            .orElse(false)
    }
}

internal fun ChatCompletion.containsToolCall(toolName: String): Boolean {
    return choices().any { choice ->
        choice.message().toolCalls()
            .map { toolCalls -> toolCalls.any { it.name == toolName } }
            .orElse(false)
    }
}