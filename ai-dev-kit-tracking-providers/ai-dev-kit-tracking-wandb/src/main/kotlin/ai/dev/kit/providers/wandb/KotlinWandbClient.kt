package ai.dev.kit.providers.wandb

import ai.dev.kit.core.fluent.KotlinLoggingClient
import ai.dev.kit.core.fluent.getUserIDFromEnv
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import java.util.*
import java.util.logging.LogManager
import java.util.logging.Logger

internal object KotlinWandbClient : KotlinLoggingClient {
    private val logger: Logger = LogManager.getLogManager().getLogger(Logger.GLOBAL_LOGGER_NAME)
        ?: Logger.getLogger(KotlinWandbClient::class.java.name)

    internal const val WANDB_API = "https://trace.wandb.ai/call"
    // W&B weave support uses weave rest api
    // docs: https://weave-docs.wandb.ai/reference/service-api/call-start-call-start-post

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

    internal fun setupCredentials(userId: String?, wandbUseApiKey: String?) {
        USER_ID = userId ?: getUserIDFromEnv()
        WANDB_USER_API_KEY = wandbUseApiKey ?: getWandbAPiKeyFromEnv()
    }

    internal lateinit var USER_ID: String
    internal lateinit var WANDB_USER_API_KEY: String
}

private fun getWandbAPiKeyFromEnv(): String {
    val wandbApiKey =
        System.getenv("WANDB_USER_API_KEY")
            ?: error("WANDB_USER_API_KEY environment variable is not set")

    return "Basic ${Base64.getEncoder().encodeToString("api:$wandbApiKey".toByteArray())}"
}
