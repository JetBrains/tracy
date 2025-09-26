package ai.dev.kit.tracing

import ai.dev.kit.exporters.addLangfuseSpanProcessor
import ai.dev.kit.exporters.addWeaveSpanProcessor
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.exporter.logging.LoggingSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor

fun setupTracing(
    tracingConfig: TracingConfig
): OpenTelemetrySdk {
    val resource = Resource.getDefault()
        .merge(
            Resource.create(
                Attributes.of(AttributeKey.stringKey("service.name"), "ai-development-kit")
            )
        )

    val tracerProvider = SdkTracerProvider.builder()
        .setResource(resource)
        .apply {
            when(tracingConfig) {
                is LangfuseConfig -> addLangfuseSpanProcessor(tracingConfig)
                is WeaveConfig -> addWeaveSpanProcessor(tracingConfig)
                is NoLoggingConfig -> {}
            }
            if (tracingConfig.traceToConsole) addLoggingSpanProcessor()
        }
        .build()

    val openTelemetry = OpenTelemetrySdk.builder()
        .setTracerProvider(tracerProvider)
        .build()

    Runtime.getRuntime().addShutdownHook(Thread {
        openTelemetry.sdkTracerProvider.shutdown()
    })

    return openTelemetry
}

private fun SdkTracerProviderBuilder.addLoggingSpanProcessor(): SdkTracerProviderBuilder {
    val spanExporter = LoggingSpanExporter.create()
    addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
    return this
}
