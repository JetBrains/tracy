package ai.dev.kit.tracing.fluent

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext


private val experimentIdContextKey: ContextKey<String> = ContextKey.named("experimentId")
val currentExperimentId: String
    get() = Context.current().get(experimentIdContextKey)
        ?: "Unset Experiment Id! Please set it with `withExperimentId` function."

private val runIdContextKey: ContextKey<String> = ContextKey.named("runId")
val currentRunId: String
    get() = Context.current().get(runIdContextKey) ?: "Unset Run Id! Please set it with `withRunId` function."

suspend fun <T> withExperimentId(id: String, block: suspend CoroutineScope.() -> T) =
    withContext(Context.current().with(experimentIdContextKey, id).asContextElement(), block)

fun <T> withExperimentIdBlocking(id: String, block: suspend CoroutineScope.() -> T) =
    runBlocking(Context.current().with(experimentIdContextKey, id).asContextElement(), block)

suspend fun <T> withRunId(id: String, block: suspend CoroutineScope.() -> T) =
    withContext(coroutineContext + Context.current().with(runIdContextKey, id).asContextElement(), block)

fun <T> withRunIdBlocking(id: String, block: suspend CoroutineScope.() -> T) =
    runBlocking(Context.current().with(runIdContextKey, id).asContextElement(), block)
