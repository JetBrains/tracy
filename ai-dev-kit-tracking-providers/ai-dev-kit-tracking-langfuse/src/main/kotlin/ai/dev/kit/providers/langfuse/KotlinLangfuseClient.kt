package ai.dev.kit.providers.langfuse

import ai.dev.kit.tracing.fluent.getUserIDFromEnv
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*

internal object KotlinLangfuseClient {
    internal const val LANGFUSE_BASE_URL = "https://langfuse.labs.jb.gg"
    // Langfuse support uses Langfuse rest api
    // docs: https://api.reference.langfuse.com/

    val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
    }

    internal fun setupCredentials(userId: String?, langfuseSecretKey: String?, langfusePublicKey: String?) {
        USER_ID = userId ?: getUserIDFromEnv()
        LANGFUSE_SECRET_KEY = langfuseSecretKey ?: getLangfuseApiSecretKeyFromEnv()
        LANGFUSE_PUBLIC_KEY = langfusePublicKey ?: getLangfuseApiPublicKeyFromEnv()
    }

    internal lateinit var USER_ID: String
    internal lateinit var LANGFUSE_PUBLIC_KEY: String
    internal lateinit var LANGFUSE_SECRET_KEY: String
}

private fun getLangfuseApiPublicKeyFromEnv(): String {
    val langfusePublicKey =
        System.getenv("LANGFUSE_PUBLIC_KEY")
            ?: error("LANGFUSE_PUBLIC_KEY environment variable is not set")
    return langfusePublicKey
}

private fun getLangfuseApiSecretKeyFromEnv(): String {
    val langfuseSecretKey =
        System.getenv("LANGFUSE_SECRET_KEY")
            ?: error("LANGFUSE_SECRET_KEY environment variable is not set")

    return langfuseSecretKey
}
