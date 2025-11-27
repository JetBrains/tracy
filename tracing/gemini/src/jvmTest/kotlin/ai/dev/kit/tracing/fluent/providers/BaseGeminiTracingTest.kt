package ai.dev.kit.tracing.fluent.providers

import ai.dev.kit.tracing.BaseAITracingTest
import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.GoogleCredentials
import com.google.genai.types.FunctionDeclaration
import com.google.genai.types.Schema
import com.google.genai.types.Tool
import org.junit.jupiter.api.TestInstance
import java.time.Duration
import com.google.genai.Client as GeminiClient
import com.google.genai.types.HttpOptions as GeminiHttpOptions

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseGeminiTracingTest : BaseAITracingTest() {
    protected val llmProviderUrl: String? = System.getenv("LLM_PROVIDER_URL")

    protected val llmProviderApiKey =
        System.getenv("GEMINI_API_KEY") ?: System.getenv("LLM_PROVIDER_API_KEY")
        ?: error("LLM_PROVIDER_API_KEY environment variable is not set")

    protected fun validateBasicTracing(model: String) {
        validateBasicTracing(llmProviderUrl!!, model)
    }

    protected fun createGeminiClient(): GeminiClient {
        val projectId = "jetbrains-grazie"
        val location = "us-central1"

        /**
         * The Gemini SDK client forbids empty credentials,
         * even if a proxy between the client and Inference attaches service account credentials
         * (e.g., LiteLLM at with passthrough [configured](https://docs.litellm.ai/docs/pass_through/vertex_ai#how-to-use)).
         */
        val dummyCredentials = object : GoogleCredentials() {
            override fun refreshAccessToken(): AccessToken = AccessToken("dummy-token", null)
        }

        return GeminiClient.builder()
            .vertexAI(true)
            .project(projectId)
            // attaches `Authorization: Bearer dummy-token` header
            .credentials(dummyCredentials)
            .location(location)
            .httpOptions(
                GeminiHttpOptions.builder()
                    .baseUrl("$llmProviderUrl/vertex_ai")
                    .headers(mapOf("x-litellm-api-key" to "Bearer $llmProviderApiKey")) // TODO: fix?
                    .timeout(Duration.ofSeconds(60).toMillis().toInt())
                    .build()
            )
            .build()
    }

    protected fun createTool(word: String): Tool {
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
}