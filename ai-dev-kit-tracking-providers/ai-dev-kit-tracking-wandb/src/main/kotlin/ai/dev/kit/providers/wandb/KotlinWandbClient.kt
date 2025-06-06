package ai.dev.kit.providers.wandb

import ai.dev.kit.tracing.fluent.getUserIDFromEnv
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import java.util.*

internal data class WandbConfig(
    val baseUrl: String,
    val userId: String,
    val apiKey: String
)

internal object KotlinWandbClient {
    private const val DEFAULT_WANDB_URL = "https://trace.wandb.ai/call"
    @Volatile
    private var _config: WandbConfig? = null
    val config: WandbConfig
        get() = _config ?: throw IllegalStateException("WandbConfig is not initialized. Call setupCredentials first.")

    val client: HttpClient by lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json()
            }
        }
    }

    /**
     * Configures the W&B client with the necessary credentials and settings.
     * Throws an exception if mandatory parameters are missing.
     *
     * @param userId The user ID used for authentication. Defaults to the "USER_ID" environment variable.
     * @param wandbApiKey The API key for W&B. Defaults to the "WANDB_USER_API_KEY" environment variable.
     * @param wandbBaseUrl The base URL of the W&B API
     *
     * @throws IllegalArgumentException If the required `wandbApiKey` is missing.
     */
    internal fun setupCredentials(
        userId: String? = getUserIDFromEnv(),
        wandbApiKey: String? = System.getenv("WANDB_USER_API_KEY"),
        wandbBaseUrl: String = DEFAULT_WANDB_URL
    ) {
        require(wandbApiKey != null) { "API key for W&B must be provided." }

        _config = WandbConfig(
            baseUrl = wandbBaseUrl,
            userId = userId ?: getUserIDFromEnv(),
            apiKey = "Basic ${Base64.getEncoder().encodeToString("api:$wandbApiKey".toByteArray())}"
        )
    }
}