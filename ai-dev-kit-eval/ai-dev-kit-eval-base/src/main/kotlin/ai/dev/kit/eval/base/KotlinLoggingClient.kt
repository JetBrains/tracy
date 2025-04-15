package ai.dev.kit.eval.base

interface KotlinLoggingClient {
    var currentExperimentId: String
    var currentRunId: String?
    fun withRun(experimentId: String): AutoCloseable
    val USER_ID: String
}

fun getUserID(): String =
    System.getenv("USER_ID")
        ?: throw IllegalStateException("USER_ID environment variable is not set")