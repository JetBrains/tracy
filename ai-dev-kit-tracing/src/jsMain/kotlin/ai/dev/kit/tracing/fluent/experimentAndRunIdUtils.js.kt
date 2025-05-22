package ai.dev.kit.tracing.fluent

import kotlinx.coroutines.CoroutineScope

actual suspend fun <T> withExperimentId(id: String, block: suspend CoroutineScope.() -> T): T =
    TODO("Implementation depends on OpenTelemetry, which is JVM-only")

actual fun <T> withExperimentIdBlocking(id: String, block: suspend CoroutineScope.() -> T): T =
    TODO("Implementation depends on OpenTelemetry, which is JVM-only")

actual suspend fun <T> withRunId(id: String, block: suspend CoroutineScope.() -> T): T =
    TODO("Implementation depends on OpenTelemetry, which is JVM-only")

actual fun <T> withRunIdBlocking(id: String, block: suspend CoroutineScope.() -> T): T =
    TODO("Implementation depends on OpenTelemetry, which is JVM-only")