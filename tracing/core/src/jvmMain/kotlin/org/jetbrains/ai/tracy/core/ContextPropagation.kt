/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.core

import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import io.opentelemetry.extension.kotlin.getOpenTelemetryContext
import kotlin.coroutines.CoroutineContext

/**
 * Returns the active OpenTelemetry [Context] from the given [CoroutineContext].
 * Falls back to [Context.current] when no trace context is attached
 * or when the resolved context is root.
 */
fun currentSpanContext(coroutineContext: CoroutineContext? = null): Context {
    val ctx = coroutineContext?.getOpenTelemetryContext() ?: return Context.current()
    return if (ctx == Context.root()) Context.current() else ctx
}

/**
 * Wraps the current OpenTelemetry [Context] as a [CoroutineContext].
 * Use this to preserve trace context across coroutines.
 */
fun currentSpanContextElement(coroutineContext: CoroutineContext? = null) =
    currentSpanContext(coroutineContext).asContextElement()
