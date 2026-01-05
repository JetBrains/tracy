package ai.jetbrains.tracy.anthropic

import ai.jetbrains.tracy.core.getFieldValue
import ai.jetbrains.tracy.core.setFieldValue
import ai.jetbrains.tracy.test.utils.BaseAITracingTest
import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonObject
import com.anthropic.core.JsonString
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.Model
import com.anthropic.models.messages.Tool
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.junit.jupiter.api.TestInstance
import java.time.Duration


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseAnthropicTracingTest : BaseAITracingTest() {
    /**
    When no value provided in ENV variable `LLM_PROVIDER_URL`, defaults to anthropic provider url in [ANTHROPIC_API_URL]
     */
    protected val llmProviderUrl: String = System.getenv("LLM_PROVIDER_URL") ?: ANTHROPIC_API_URL

    protected val llmProviderApiKey =
        System.getenv("ANTHROPIC_API_KEY") ?: System.getenv("LLM_PROVIDER_API_KEY")
        ?: error("LLM_PROVIDER_API_KEY environment variable is not set")

    protected fun createAnthropicClient(
        url: String? = llmProviderUrl,
        apiKey: String = llmProviderApiKey,
        timeout: Duration = Duration.ofSeconds(60)
    ): AnthropicClient {
        return AnthropicOkHttpClient.builder()
            .baseUrl(url)
            .apiKey(apiKey)
            .timeout(timeout)
            .build()
    }

    protected fun validateBasicTracing(model: Model) {
        validateBasicTracing(llmProviderUrl, model.asString())
    }

    protected fun createTool(word: String): Tool {
        return Tool.builder()
            .type(Tool.Type.CUSTOM)
            .name(word)
            .description("Say $word to the user")
            .inputSchema(
                Tool.InputSchema.builder()
                    .type(JsonString.of("object"))
                    .properties(
                        JsonObject.of(
                            mapOf(
                                "name" to JsonObject.of(
                                    mapOf(
                                        "type" to JsonString.of("string"),
                                        "description" to JsonString.of("Say $word to a person")
                                    )
                                )
                            )
                        )
                    )
                    .required(listOf("name"))
                    .build()
            ).build()
    }

    protected fun installHttpInterceptor(client: AnthropicClient, interceptor: Interceptor) {
        val clientOptions = getFieldValue(client, "clientOptions")
        val originalHttpClient = getFieldValue(clientOptions, "originalHttpClient")

        // Find the object that actually holds the "okHttpClient" field
        val okHttpHolder = if (originalHttpClient::class.simpleName == "OkHttpClient") {
            originalHttpClient
        } else {
            getFieldValue(originalHttpClient, "httpClient")
        }

        val okHttpClient = getFieldValue(okHttpHolder, "okHttpClient") as OkHttpClient
        val modifiedHttpClient = okHttpClient.newBuilder().addInterceptor(interceptor).build()

        setFieldValue(okHttpHolder, "okHttpClient", modifiedHttpClient)
    }

    companion object {
        private const val ANTHROPIC_API_URL = "https://api.anthropic.com"
    }
}

internal fun Message.containsToolCall(toolName: String): Boolean {
    return content().any { block ->
        block.isToolUse() && block.asToolUse().name() == toolName
    }
}