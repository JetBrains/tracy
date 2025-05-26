package ai.dev.kit.tracing.fluent

import kotlinx.coroutines.CoroutineScope

expect object TracingSessionProvider {
    val currentExperimentId: String
    val currentRunId: String
}

expect suspend fun <T> withExperimentId(id: String, block: suspend CoroutineScope.() -> T): T
expect fun <T> withExperimentIdBlocking(id: String, block: suspend CoroutineScope.() -> T): T
expect suspend fun <T> withRunId(id: String, block: suspend CoroutineScope.() -> T): T
expect fun <T> withRunIdBlocking(id: String, block: suspend CoroutineScope.() -> T): T
