package ai.dev.kit.tracing.fluent

actual object TracingSessionProvider {
    actual val currentExperimentId: String = TODO("Impl relies on OpenTelemetry, which is JVM-only")
    actual val currentRunId: String = TODO("Impl relies on OpenTelemetry, which is JVM-only")
}