package org.example.ai.mlflow.fluent.processor

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import net.bytebuddy.agent.ByteBuddyAgent

object TracingFlowProcessor {
    fun setup() {
        setupTracing()
        setupTracingFlowAgent()
    }

    private fun setupTracingFlowAgent() {
        TracingFlowDecoratorAgent.premain(ByteBuddyAgent.install())
    }

    private fun setupTracing() {
        val tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(RootSpanExporter()))
            .build()

        GlobalOpenTelemetry.set(
            OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build()
        )
    }
}
