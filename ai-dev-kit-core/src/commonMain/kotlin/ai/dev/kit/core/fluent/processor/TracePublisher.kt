package ai.dev.kit.core.fluent.processor

interface TracePublisher {
    suspend fun publishTrace(trace: List<SpanData>)
}
