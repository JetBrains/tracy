package ai.dev.kit.tracing.fluent.processor

interface TracePublisher {
    suspend fun publishTrace(trace: List<SpanData>)
}
