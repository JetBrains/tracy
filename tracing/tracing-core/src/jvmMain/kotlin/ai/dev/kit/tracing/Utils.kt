package ai.dev.kit.tracing

import ai.dev.kit.exporters.addLangfuseSpanProcessor
import ai.dev.kit.exporters.addOtlpFileSpanProcessor
import ai.dev.kit.exporters.addWeaveSpanProcessor
import ai.dev.kit.tracing.fluent.FluentSpanAttributes
import ai.dev.kit.tracing.fluent.processor.Span
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.exporter.logging.LoggingSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder
import io.opentelemetry.sdk.trace.SpanLimits
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import ai.dev.kit.tracing.fluent.processor.currentSpanContext
import io.opentelemetry.exporter.logging.otlp.OtlpJsonLoggingSpanExporter
import kotlin.coroutines.CoroutineContext

fun configureOpenTelemetrySdk(
    tracingConfig: TracingConfig
): OpenTelemetrySdk {
    val resource = Resource.getDefault().merge(
        Resource.create(
            Attributes.of(AttributeKey.stringKey("service.name"), "ai-development-kit")
        )
    )

    val maxNumberOfSpanAttributes: Int =
        tracingConfig.maxNumberOfSpanAttributes ?: System.getenv("MAX_NUMBER_OF_SPAN_ATTRIBUTES")?.toIntOrNull()
        ?: DEFAULT_NUMBER_OF_SPAN_ATTRIBUTES

    val maxSpanAttributeValueLength: Int =
        tracingConfig.maxSpanAttributeValueLength ?: System.getenv("MAX_SPAN_ATTRIBUTE_VALUE_LENGTH")?.toIntOrNull()
        ?: DEFAULT_SPAN_ATTRIBUTE_VALUE_LENGTH

    require(maxNumberOfSpanAttributes > 0) {
        "Max Number Of Span Attributes must be greater than 0"
    }

    require(maxSpanAttributeValueLength > 0) {
        "Max Span Attribute Value Length must be greater than 0"
    }

    val spanLimits = SpanLimits.builder().setMaxNumberOfAttributes(maxNumberOfSpanAttributes)
        .setMaxAttributeValueLength(maxSpanAttributeValueLength).build()

    val tracerProvider = SdkTracerProvider.builder()
        .setSpanLimits(spanLimits)
        .setResource(resource)
        .apply {
            when (tracingConfig) {
                is LangfuseConfig -> addLangfuseSpanProcessor(tracingConfig)
                is WeaveConfig -> addWeaveSpanProcessor(tracingConfig)
                is FileConfig -> addOtlpFileSpanProcessor(tracingConfig)
                is ConsoleConfig -> addConsoleSpanProcessor(tracingConfig)
            }
            // don't add a logging span processor when already exporting to CLI
            if (tracingConfig.traceToConsole && tracingConfig !is ConsoleConfig) {
                addLoggingSpanProcessor()
            }
        }
        .build()

    val openTelemetry = OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build()

    Runtime.getRuntime().addShutdownHook(Thread {
        openTelemetry.sdkTracerProvider.shutdown()
    })

    return openTelemetry
}

private fun SdkTracerProviderBuilder.addConsoleSpanProcessor(
    consoleConfig: ConsoleConfig,
): SdkTracerProviderBuilder {
    val spanExporter = when (consoleConfig.format) {
        OutputFormat.PLAIN_TEXT -> LoggingSpanExporter.create()
        OutputFormat.JSON -> OtlpJsonLoggingSpanExporter.create()
    }
    addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
    return this
}

private fun SdkTracerProviderBuilder.addLoggingSpanProcessor(): SdkTracerProviderBuilder {
    val spanExporter = LoggingSpanExporter.create()
    addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
    return this
}

/**
 * Adds a list of Langfuse trace tags to the current active span within an OpenTelemetry trace.
 *
 * @param tags A list of tag strings to attach to the current Langfuse trace.
 * @param coroutineContext Optional coroutine context used to resolve the OpenTelemetry context.
 *                         If `null`, the current active context is used.
 */
fun addLangfuseTagsToCurrentTrace(tags: List<String>, coroutineContext: CoroutineContext? = null) {
    val otelContext = currentSpanContext(coroutineContext)
    Span.fromContext(otelContext).setAttribute(FluentSpanAttributes.LANGFUSE_TRACE_TAGS.key, tags.toString())
}
