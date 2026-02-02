package ai.jetbrains.tracy.core

import ai.jetbrains.tracy.core.fluent.FluentSpanAttributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import kotlin.coroutines.CoroutineContext

fun Span.addExceptionAttributes(exception: Throwable) {
    this.recordException(exception)
    this.setStatus(StatusCode.ERROR, exception.message ?: "Unknown error")
}
