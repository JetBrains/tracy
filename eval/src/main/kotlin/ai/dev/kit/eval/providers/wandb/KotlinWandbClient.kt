package ai.dev.kit.eval.providers.wandb

import ai.dev.kit.eval.utils.getUserIDFromEnv
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import java.util.*
import kotlin.invoke

internal object KotlinWandbClient {
    internal const val WANDB_API = "https://trace.wandb.ai/call"
    // W&B weave support uses weave rest api
    // docs: https://weave-docs.wandb.ai/reference/service-api/call-start-call-start-post

    val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
    }

    internal fun setupCredentials(userId: String?, wandbUseApiKey: String?) {
        USER_ID = userId ?: getUserIDFromEnv()

        val rawWandbApiKey = wandbUseApiKey ?: getWandbAPiKeyFromEnv()
        WANDB_USER_API_KEY = "Basic ${Base64.getEncoder().encodeToString("api:$rawWandbApiKey".toByteArray())}"
    }

    internal lateinit var USER_ID: String
    internal lateinit var WANDB_USER_API_KEY: String
}

private fun getWandbAPiKeyFromEnv(): String {
    return System.getenv("WANDB_USER_API_KEY")
        ?: error("WANDB_USER_API_KEY environment variable is not set")
}
