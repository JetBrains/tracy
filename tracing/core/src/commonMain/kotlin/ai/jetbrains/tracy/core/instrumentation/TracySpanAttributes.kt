/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package ai.jetbrains.tracy.core.instrumentation

/**
 * Defines standard attribute keys used for Tracy tracing spans.
 *
 * These attributes are attached to OpenTelemetry spans to record
 * function inputs, outputs, and execution metadata.
 */
enum class TracySpanAttributes(val key: String) {
    SPAN_INPUTS("input"),
    SPAN_OUTPUTS("output"),
    SESSION_ID("session.id"),
    CODE_FUNCTION_NAME("code.function.name"),
    LANGFUSE_TRACE_TAGS("langfuse.trace.tags");
}