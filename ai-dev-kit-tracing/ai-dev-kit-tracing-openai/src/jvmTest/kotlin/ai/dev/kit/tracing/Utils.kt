package ai.dev.kit.tracing

import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import tracing.ai.dev.kit.tracing.LITELLM_URL
import java.time.Duration

fun createLiteLLMClient(): OpenAIClient {
    return OpenAIOkHttpClient.builder()
        .baseUrl(LITELLM_URL)
        .apiKey(System.getenv("LITELLM_API_KEY") ?: error("LITELLM_API_KEY environment variable is not set"))
        .timeout(Duration.ofSeconds(60))
        .build()
}
