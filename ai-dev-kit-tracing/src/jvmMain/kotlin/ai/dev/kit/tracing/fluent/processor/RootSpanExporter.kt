package ai.dev.kit.tracing.fluent.processor

import io.opentelemetry.api.trace.SpanId
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.kodein.di.instance
import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList

class RootSpanExporter(val scope: CoroutineScope) : SpanExporter {
    private val logger = LoggerFactory.getLogger(RootSpanExporter::class.java)
    private val spanGroups = ConcurrentHashMap<String, MutableList<SpanData>>()

    val SpanData.isRootSpan: Boolean
        get() = this.parentSpanId == SpanId.getInvalid()

    override fun export(spans: Collection<SpanData>): CompletableResultCode {
        val exportResult = CompletableResultCode()
        for (span in spans) {
            val traceId = span.traceId
            val spanList = spanGroups.computeIfAbsent(traceId) { CopyOnWriteArrayList() }
            spanList.add(span)
            // The root span is finished; send it to the provider
            if (span.isRootSpan) {
                val spansToPublish = spanList.toList()
                spanGroups.remove(traceId)
                scope.launch {
                    try {
                        val tracePublisher: TracePublisher by TracingFlowProcessor.di.instance()
                        logger.debug("Publishing trace with traceId=$traceId, spans=${spansToPublish.size}")
                        tracePublisher.publishTrace(spansToPublish)
                        exportResult.succeed()
                    } catch (e: Exception) {
                        logger.error("Failed to publish trace for traceId=$traceId with ${spansToPublish.size} spans", e)
                        exportResult.fail()
                    }
                }
            }
        }
        return exportResult
    }

    override fun flush(): CompletableResultCode {
        val flushResult = CompletableResultCode()
        runBlocking {
            try {
                scope.coroutineContext.job.children.forEach { it.join() }
                flushResult.succeed()
            } catch (e: Exception) {
                logger.error("Failed to flush pending spans", e)
                flushResult.fail()
            }
        }
        return flushResult
    }


    override fun shutdown(): CompletableResultCode {
        scope.cancel()
        return CompletableResultCode.ofSuccess()
    }
}
