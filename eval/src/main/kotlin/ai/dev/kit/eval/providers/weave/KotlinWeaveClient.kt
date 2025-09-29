package ai.dev.kit.eval.providers.weave

import ai.dev.kit.eval.utils.getUserIDFromEnv
import java.util.*

internal object KotlinWeaveClient {
    internal fun setupCredentials(userId: String?, weaveUseApiKey: String?) {
        USER_ID = userId ?: getUserIDFromEnv()

        val rawWeaveApiKey = weaveUseApiKey ?: getWeaveApiKeyFromEnv()
        WEAVE_USER_API_KEY = "Basic ${Base64.getEncoder().encodeToString("api:$rawWeaveApiKey".toByteArray())}"
    }

    internal lateinit var USER_ID: String
    internal lateinit var WEAVE_USER_API_KEY: String
}

private fun getWeaveApiKeyFromEnv(): String {
    return System.getenv("WEAVE_USER_API_KEY")
        ?: error("WEAVE_USER_API_KEY environment variable is not set")
}
