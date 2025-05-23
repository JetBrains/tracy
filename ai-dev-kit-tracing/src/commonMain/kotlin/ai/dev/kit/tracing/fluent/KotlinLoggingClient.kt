package ai.dev.kit.tracing.fluent

// Used not only for tracing but is to be deleted
// TODO: delete when possible
interface KotlinLoggingClient {
    var currentExperimentId: String
    fun withRun(experimentId: String): AutoCloseable
}

expect fun getUserIDFromEnv(): String