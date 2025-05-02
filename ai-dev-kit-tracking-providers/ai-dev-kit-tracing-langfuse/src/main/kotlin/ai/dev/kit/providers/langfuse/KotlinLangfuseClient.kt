package ai.dev.kit.providers.langfuse

import ai.dev.kit.core.fluent.KotlinLoggingClient
import ai.dev.kit.core.fluent.getUserID
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import java.util.logging.LogManager
import java.util.logging.Logger

internal object KotlinLangfuseClient : KotlinLoggingClient {
    private val logger: Logger = LogManager.getLogManager().getLogger(Logger.GLOBAL_LOGGER_NAME)
        ?: Logger.getLogger(KotlinLangfuseClient::class.java.name)

    internal const val LANGFUSE_BASE_URL = "https://langfuse.labs.jb.gg/"
    // Langfuse support uses Langfuse rest api
    // docs: https://api.reference.langfuse.com/

    // TODO: Remove state storage here ASAP!
    override var currentExperimentId: String = "0"
    override var currentRunId: String? = null

    const val TEST_PROJECT_NAME = "ai-dev-kit-tracing-tests"

    val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
    }

    override fun withRun(experimentId: String) = object : AutoCloseable {
        override fun close() {
        }
    }

    override val USER_ID: String = getUserID()
    internal val LANGFUSE_PUBLIC_KEY: String = getLangfuseApiPublicKey()
    internal val LANGFUSE_SECRET_KEY: String = getLangfuseApiSecretKey()
}

private fun getLangfuseApiPublicKey(): String {
    val langfusePublicKey =
        System.getenv("LANGFUSE_PUBLIC_KEY")
            ?: error("LANGFUSE_PUBLIC_KEY environment variable is not set")
    return langfusePublicKey
}

private fun getLangfuseApiSecretKey(): String {
    val langfuseSecretKey =
        System.getenv("LANGFUSE_SECRET_KEY")
            ?: error("LANGFUSE_SECRET_KEY environment variable is not set")

    return langfuseSecretKey
}
