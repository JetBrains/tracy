package ai.dev.kit.core.fluent

interface KotlinLoggingClient {
    var currentExperimentId: String
    var currentRunId: String?
    fun withRun(experimentId: String): AutoCloseable
}

expect fun getUserIDFromEnv(): String