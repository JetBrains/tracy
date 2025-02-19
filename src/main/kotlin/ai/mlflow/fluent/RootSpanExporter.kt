package org.example.ai.mlflow.fluent

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.SpanId
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter
import kotlinx.coroutines.runBlocking
import org.example.ai.mlflow.updateTrace

class RootSpanExporter : SpanExporter {
    private val spanGroups = mutableMapOf<String, MutableList<SpanData>>()

    data class TraceInfo(
        val parent: SpanData,
        val spans: List<SpanData>
    )

    override fun export(spans: Collection<SpanData>): CompletableResultCode {
        val tracesToExport = mutableListOf<TraceInfo>()
        for (span in spans) {
            val traceId = span.traceId
            val spanList = spanGroups.computeIfAbsent(traceId) { mutableListOf() }
            spanList.add(span)
            if (span.parentSpanId == SpanId.getInvalid()) {
                tracesToExport.add(TraceInfo(parent = span, spans = spanList.toList()))
                spanGroups.remove(traceId)
            }
        }
        for (trace in tracesToExport) {
            // TODO get rid of run blocking
            runBlocking {
                updateTrace(trace.parent, trace.spans)
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

fun setupTracing() {
    val tracerProvider = SdkTracerProvider.builder()
        .addSpanProcessor(SimpleSpanProcessor.create(RootSpanExporter()))
        .build()

    GlobalOpenTelemetry.set(
        OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .build()
    )
}
