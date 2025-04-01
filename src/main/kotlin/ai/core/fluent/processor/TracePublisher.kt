package ai.core.fluent.processor

import io.opentelemetry.sdk.trace.data.SpanData

interface TracePublisher {
    suspend fun publishTrace(trace: List<SpanData>)
}
