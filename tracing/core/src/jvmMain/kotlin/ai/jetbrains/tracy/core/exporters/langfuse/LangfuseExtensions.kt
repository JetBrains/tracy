/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package ai.jetbrains.tracy.core.exporters.langfuse

import ai.jetbrains.tracy.core.currentSpanContext
import ai.jetbrains.tracy.core.instrumentation.TracySpanAttributes
import io.opentelemetry.api.trace.Span
import kotlin.coroutines.CoroutineContext

/**
 * Adds a list of Langfuse trace tags to the current active span within an OpenTelemetry trace.
 *
 * @param tags A list of tag strings to attach to the current Langfuse trace.
 * @param coroutineContext Optional coroutine context used to resolve the OpenTelemetry context.
 *                         If `null`, the current active context is used.
 */
fun addLangfuseTagsToCurrentTrace(tags: List<String>, coroutineContext: CoroutineContext? = null) {
    val otelContext = currentSpanContext(coroutineContext)
    Span.fromContext(otelContext).setAttribute(TracySpanAttributes.LANGFUSE_TRACE_TAGS.key, tags.toString())
}
