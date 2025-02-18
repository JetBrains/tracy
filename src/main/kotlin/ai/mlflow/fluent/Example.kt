package org.example.ai.mlflow.fluent

import com.google.inject.Guice
import com.google.inject.Injector
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class ParentChildLoggingSpanExporter : SpanExporter {
    override fun export(spans: MutableCollection<SpanData>): CompletableResultCode {
        for (span in spans) {
            println(
                "Span: ${span.name}, Span ID: ${span.spanId}, Parent Span ID: ${span.parentSpanId}, Trace ID: ${span.traceId}"
            )
        }
        return CompletableResultCode.ofSuccess()
    }

    override fun shutdown(): CompletableResultCode {
        return CompletableResultCode.ofSuccess()
    }

    override fun flush(): CompletableResultCode {
        return CompletableResultCode.ofSuccess()
    }
}

fun setupTracing() {
    val tracerProvider = SdkTracerProvider.builder()
        .addSpanProcessor(SimpleSpanProcessor.create(ParentChildLoggingSpanExporter()))
        .build()

    GlobalOpenTelemetry.set(
        OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .build()
    )
}

open class MyClass {
    @KotlinFlowTrace
    open fun someFunction(x: Int, y: Int, z: Int = 2): Int {
        Thread.sleep(500)
        return a(x) + b(y - z)
    }

    @KotlinFlowTrace
    open fun a(x: Int): Int {
        Thread.sleep(200)
        return x - 1
    }

    @KotlinFlowTrace
    open fun b(x: Int): Int {
        Thread.sleep(200)
        return x + 1
    }
}

fun main() {
    runBlocking {
        coroutineScope {
            setupTracing()
            val injector: Injector = Guice.createInjector(KotlinFlowTraceModule())
            val myClass = injector.getInstance(MyClass::class.java)

            launch {
                myClass.someFunction(2, 4)
            }
            launch {
                myClass.someFunction(3, 5)
            }
        }
    }
}