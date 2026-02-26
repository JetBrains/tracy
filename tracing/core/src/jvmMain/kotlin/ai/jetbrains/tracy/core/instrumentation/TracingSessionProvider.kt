/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package ai.jetbrains.tracy.core.instrumentation

import io.opentelemetry.context.Context
import io.opentelemetry.context.ContextKey
import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

private val SESSION_ID_CONTEXT_KEY: ContextKey<String> = ContextKey.named("projectId")
private val PROJECT_ID_CONTEXT_KEY: ContextKey<String> = ContextKey.named("sessionId")

actual object TracingSessionProvider {
    actual val currentProjectId: String?
        get() = Context.current().get(PROJECT_ID_CONTEXT_KEY)

    actual val currentSessionId: String?
        get() = Context.current().get(SESSION_ID_CONTEXT_KEY)
}

actual suspend fun <T> withProjectId(id: String, block: suspend CoroutineScope.() -> T): T =
    withContext(Context.current().with(PROJECT_ID_CONTEXT_KEY, id).asContextElement(), block)

actual fun <T> withProjectIdBlocking(id: String, block: suspend CoroutineScope.() -> T): T =
    runBlocking(Context.current().with(PROJECT_ID_CONTEXT_KEY, id).asContextElement(), block)

actual suspend fun <T> withSessionId(id: String, block: suspend CoroutineScope.() -> T): T =
    withContext(Context.current().with(SESSION_ID_CONTEXT_KEY, id).asContextElement(), block)

actual fun <T> withSessionIdBlocking(id: String?, block: suspend CoroutineScope.() -> T): T {
    val context = Context.current().let { ctx ->
        if (id == null) ctx else ctx.with(SESSION_ID_CONTEXT_KEY, id)
    }
    return runBlocking(context.asContextElement(), block)
}