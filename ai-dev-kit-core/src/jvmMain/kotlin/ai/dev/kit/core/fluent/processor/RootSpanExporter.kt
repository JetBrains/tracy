package ai.dev.kit.core.fluent.processor

import io.opentelemetry.api.trace.SpanId
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap

class RootSpanExporter(val tracePublisher: TracePublisher) : SpanExporter {
    private val spanGroups = ConcurrentHashMap<String, MutableList<SpanData>>()

    override fun export(spans: Collection<SpanData>): CompletableResultCode {
        synchronized(spanGroups) {
            for (span in spans) {
                val traceId = span.traceId
                val spanList = spanGroups.computeIfAbsent(traceId) { mutableListOf() }
                spanList.add(span)
                if (span.parentSpanId == SpanId.getInvalid()) {
                    // TODO get rid of run blocking
                    runBlocking {
                        tracePublisher.publishTrace(spanList)
                    }
                    spanGroups.remove(traceId)
                }
            }
        }
        return CompletableResultCode.ofSuccess()
    }

    override fun flush(): CompletableResultCode {
        return CompletableResultCode.ofSuccess()
    }

    override fun shutdown(): CompletableResultCode {
        return CompletableResultCode.ofSuccess()
    }
}
