package ai.dev.kit.tracing.fluent

import io.opentelemetry.context.Context
import io.opentelemetry.context.ContextKey
import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

private val RUN_ID_CONTEXT_KEY: ContextKey<String> = ContextKey.named("experimentId")
private val EXPERIMENT_ID_CONTEXT_KEY: ContextKey<String> = ContextKey.named("runId")

actual object TracingSessionProvider {
    const val UNSET_EXPERIMENT_ID_TEXT = "Unset Experiment Id! Please set it with `withExperimentId` function."
    const val UNSET_RUN_ID_TEXT = "Unset Run Id! Please set it with `withRunId` function."

    actual val currentProjectId: String
        get() = Context.current().get(EXPERIMENT_ID_CONTEXT_KEY)
            ?: UNSET_EXPERIMENT_ID_TEXT

    actual val currentSessionId: String
        get() = Context.current().get(RUN_ID_CONTEXT_KEY)
            ?: UNSET_RUN_ID_TEXT
}

actual suspend fun <T> withProjectId(id: String, block: suspend CoroutineScope.() -> T): T =
    withContext(Context.current().with(EXPERIMENT_ID_CONTEXT_KEY, id).asContextElement(), block)

actual fun <T> withProjectIdBlocking(id: String, block: suspend CoroutineScope.() -> T): T =
    runBlocking(Context.current().with(EXPERIMENT_ID_CONTEXT_KEY, id).asContextElement(), block)

actual suspend fun <T> withSessionId(id: String, block: suspend CoroutineScope.() -> T): T =
    withContext(Context.current().with(RUN_ID_CONTEXT_KEY, id).asContextElement(), block)

actual fun <T> withSessionIdBlocking(id: String, block: suspend CoroutineScope.() -> T): T =
    runBlocking(Context.current().with(RUN_ID_CONTEXT_KEY, id).asContextElement(), block)
