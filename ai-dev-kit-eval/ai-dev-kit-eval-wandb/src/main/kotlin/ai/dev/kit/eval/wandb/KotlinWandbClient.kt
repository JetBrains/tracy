package ai.dev.kit.eval.wandb

import ai.dev.kit.eval.base.KotlinLoggingClient
import ai.dev.kit.eval.base.getUserID
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
    internal val WANDB_USER_API_KEY: String = getWandbAPiKey()
}

fun getWandbAPiKey(): String {
    val wandbApiKey =
        System.getenv("WANDB_USER_API_KEY")
            ?: throw IllegalStateException("WANDB_USER_API_KEY environment variable is not set")

    return "Basic ${Base64.getEncoder().encodeToString("api:$wandbApiKey".toByteArray())}"
}
