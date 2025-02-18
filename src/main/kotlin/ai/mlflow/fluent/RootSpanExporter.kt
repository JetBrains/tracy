package org.example.ai.mlflow.fluent

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter

class RootSpanExporter : SpanExporter {
    private val spanGroups = mutableMapOf<String, MutableList<SpanData>>()

    override fun export(spans: Collection<SpanData>): CompletableResultCode {
        val tracesToExport = mutableListOf<List<SpanData>>()
        for (span in spans) {
            val traceId = span.traceId
            val spanList = spanGroups.computeIfAbsent(traceId) { mutableListOf() }
            spanList.add(span)
            if (span.parentSpanId == EMPTY_PARENT_ID) {
                tracesToExport.add(spanList.toList())
                spanGroups.remove(traceId)
            }
        }
        for (trace in tracesToExport) {
            exportTrace(trace)
        }
        return CompletableResultCode.ofSuccess()
    }

    private fun exportTrace(trace: List<SpanData>) {
        println("Exporting full trace with ${trace.size} spans:")
        for (span in trace) {
            println(
                "  Span: ${span.name}, Span ID: ${span.spanId}, Parent Span ID: ${span.parentSpanId}, Trace ID: ${span.traceId}"
            )
        }
    }

    override fun flush(): CompletableResultCode {
        return CompletableResultCode.ofSuccess()
    }

    override fun shutdown(): CompletableResultCode {
        return CompletableResultCode.ofSuccess()
    }

    companion object {
        private const val EMPTY_PARENT_ID = "0000000000000000"
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
