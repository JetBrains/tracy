package ai.dev.kit.core.fluent

interface KotlinLoggingClient {
    var currentExperimentId: String
    var currentRunId: String?
    fun withRun(experimentId: String): AutoCloseable
    val USER_ID: String
}

expect fun getUserID(): String