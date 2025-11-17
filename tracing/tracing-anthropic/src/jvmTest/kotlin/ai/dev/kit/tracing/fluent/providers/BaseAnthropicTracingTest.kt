package ai.dev.kit.tracing.fluent.providers

import ai.dev.kit.getFieldValue
import ai.dev.kit.setFieldValue
import ai.dev.kit.tracing.BaseOpenTelemetryTracingTest
import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonObject
import com.anthropic.core.JsonString
import com.anthropic.models.messages.Tool
import okhttp3.Interceptor
import org.junit.jupiter.api.TestInstance
import java.time.Duration


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseAnthropicTracingTest : BaseOpenTelemetryTracingTest() {
    /**
    When no value provided, defaults to anthropic provider url in [AnthropicOkHttpClient.Builder]
     */
    protected val llmProviderUrl: String? = System.getenv("LLM_PROVIDER_URL")

    protected val llmProviderApiKey =
        System.getenv("ANTHROPIC_API_KEY") ?: System.getenv("LLM_PROVIDER_API_KEY")
        ?: error("LLM_PROVIDER_API_KEY environment variable is not set")

    protected fun createAnthropicClient(): AnthropicClient {
        return AnthropicOkHttpClient.builder()
            .baseUrl(llmProviderUrl)
            .apiKey(llmProviderApiKey)
            .timeout(Duration.ofSeconds(60))
            .build()
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

        val okHttpClient = getFieldValue(okHttpHolder, "okHttpClient") as okhttp3.OkHttpClient
        val modifiedHttpClient = okHttpClient.newBuilder().addInterceptor(interceptor).build()

        setFieldValue(okHttpHolder, "okHttpClient", modifiedHttpClient)
    }

    companion object {
        protected const val CAT_IMAGE_URL = "https://images.pexels.com/photos/104827/cat-pet-animal-domestic-104827.jpeg"
        protected const val SAMPLE_PDF_FILE_URL = "https://pdfobject.com/pdf/sample.pdf"
    }
}