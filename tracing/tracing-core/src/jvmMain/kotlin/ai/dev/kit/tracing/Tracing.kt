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
import io.opentelemetry.sdk.trace.SpanLimits
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

    val maxNumberOfSpanAttributes: Int =
        tracingConfig.maxNumberOfSpanAttributes ?: System.getenv("MAX_NUMBER_OF_SPAN_ATTRIBUTES")?.toIntOrNull()
        ?: MAX_NUMBER_OF_SPAN_ATTRIBUTES

    val maxSpanAttributeValueLength: Int =
        tracingConfig.maxSpanAttributeValueLength ?: System.getenv("MAX_SPAN_ATTRIBUTE_VALUE_LENGTH")?.toIntOrNull()
        ?: MAX_SPAN_ATTRIBUTE_VALUE_LENGTH

    val spanLimits = SpanLimits.builder()
        .setMaxNumberOfAttributes(maxNumberOfSpanAttributes)
        .setMaxAttributeValueLength(maxSpanAttributeValueLength)
        .build()

    TracingManager.maxNumberOfSpanAttributes = maxNumberOfSpanAttributes

    val tracerProvider = SdkTracerProvider.builder()
        .setSpanLimits(spanLimits)
        .setResource(resource)
        .apply {
            when (tracingConfig) {
                is LangfuseConfig -> addLangfuseSpanProcessor(tracingConfig)
                is WeaveConfig -> addWeaveSpanProcessor(tracingConfig)
                is ConsoleConfig -> {}
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
