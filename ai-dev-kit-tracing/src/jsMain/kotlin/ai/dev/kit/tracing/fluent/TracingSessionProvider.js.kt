package ai.dev.kit.tracing.fluent

import kotlinx.coroutines.CoroutineScope

actual object TracingSessionProvider {
    actual val currentProjectId: String = TODO("Impl relies on OpenTelemetry, which is JVM-only")
    actual val currentSessionId: String = TODO("Impl relies on OpenTelemetry, which is JVM-only")
}

actual suspend fun <T> withProjectId(id: String, block: suspend CoroutineScope.() -> T): T =
    TODO("Implementation depends on OpenTelemetry, which is JVM-only")

actual fun <T> withProjectIdBlocking(id: String, block: suspend CoroutineScope.() -> T): T =
    TODO("Implementation depends on OpenTelemetry, which is JVM-only")

actual suspend fun <T> withSessionId(id: String, block: suspend CoroutineScope.() -> T): T =
    TODO("Implementation depends on OpenTelemetry, which is JVM-only")

actual fun <T> withSessionIdBlocking(id: String, block: suspend CoroutineScope.() -> T): T =
    TODO("Implementation depends on OpenTelemetry, which is JVM-only")
