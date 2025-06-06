package ai.dev.kit.providers.langfuse

import ai.dev.kit.tracing.fluent.getUserIDFromEnv
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*

internal data class LangfuseConfig(
    val baseUrl: String,
    val userId: String,
    val publicKey: String,
    val secretKey: String
)

internal object KotlinLangfuseClient {
    private const val DEFAULT_JB_LANGFUSE_URL = "https://langfuse.labs.jb.gg"
    @Volatile
    private var _config: LangfuseConfig? = null
    val config: LangfuseConfig
        get() = _config ?: throw IllegalStateException("LangfuseConfig is not initialized. Call setupCredentials first.")
    // Langfuse support uses Langfuse rest api
    // docs: https://api.reference.langfuse.com/
    val client: HttpClient by lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json()
            }
        }
    }

    /**
     * Configures the Langfuse client with the necessary credentials and settings.
     * Throws an exception if mandatory parameters are missing.
     *
     * @param userId The user ID used for authentication. Defaults to the "USER_ID" environment variable.
     * @param langfusePublicKey The public API key for Langfuse. Defaults to the "LANGFUSE_PUBLIC_KEY" environment variable.
     * @param langfuseSecretKey The private API key for Langfuse. Defaults to the "LANGFUSE_SECRET_KEY" environment variable.
     * @param langfuseBaseUrl The base URL of the Langfuse API. Defaults to the predefined JetBrains instance URL if not specified.
     *
     * @throws IllegalArgumentException If the required `langfusePublicKey` or `langfuseSecretKey` is missing.
     */
    internal fun setupCredentials(
        userId: String? = getUserIDFromEnv(),
        langfusePublicKey: String? = System.getenv("LANGFUSE_PUBLIC_KEY"),
        langfuseSecretKey: String? = System.getenv("LANGFUSE_SECRET_KEY"),
        langfuseBaseUrl: String = DEFAULT_JB_LANGFUSE_URL
    ) {
        require(langfuseSecretKey != null) { "Secret key for Langfuse must be provided." }
        require(langfusePublicKey != null) { "Public key for Langfuse must be provided." }

        _config = LangfuseConfig(
            baseUrl = langfuseBaseUrl,
            userId = userId ?: getUserIDFromEnv(),
            publicKey = langfusePublicKey,
            secretKey = langfuseSecretKey
        )
    }
}