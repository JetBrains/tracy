package ai.jetbrains.tracy.core

import ai.jetbrains.tracy.core.exporters.BaseExporterConfig
import ai.jetbrains.tracy.core.exporters.ConsoleExporterConfig
import ai.jetbrains.tracy.core.exporters.ExporterCommonSettings
import ai.jetbrains.tracy.core.exporters.otlp.LangfuseExporterConfig
import ai.jetbrains.tracy.core.exporters.otlp.WeaveExporterConfig
import ai.jetbrains.tracy.core.fluent.FluentSpanAttributes
import ai.jetbrains.tracy.core.fluent.processor.currentSpanContext
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

/**
 * Initializes the [OpenTelemetrySdk] using a provided [BaseExporterConfig].
 *
 * This method sets up a [SdkTracerProvider] with:
 *  - Span processors for the given configuration.
 *  - Span limits.
 *  - A shutdown hook to flush and shut down traces gracefully if [ExporterCommonSettings.flushOnShutdown] is true.
 *
 * @param exporterConfig the exporter configuration defining how spans should be exported.
 *  Examples include:
 *  - [LangfuseExporterConfig] sends spans to a Langfuse OTLP endpoint.
 *  - [WeaveExporterConfig] sends spans to a W&B Weave OTLP endpoint.
 *  - [ConsoleExporterConfig] logs spans to the console only (for local debugging).
 * @param additionalResource optional extra [Resource] attributes merged with the default
 *  OpenTelemetry resource. Use this to set values such as "service.name"
 *
 * @return the initialized [OpenTelemetrySdk] instance.
 */
fun configureOpenTelemetrySdk(
    exporterConfig: BaseExporterConfig,
    additionalResource: Resource = Resource.create(
        Attributes.of(AttributeKey.stringKey("service.name"), "unknown-service")
    )
): OpenTelemetrySdk {
    val resource = Resource.getDefault().merge(additionalResource)

    val tracerProvider = SdkTracerProvider.builder()
        .setResource(resource)
        .setSpanLimits(exporterConfig.createSpanLimits())
        .apply {
            exporterConfig.configureSpanProcessors(this)
        }.build()

    val openTelemetry = OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build()

    if (exporterConfig.settings.flushOnShutdown) {
        Runtime.getRuntime().addShutdownHook(Thread {
            openTelemetry.sdkTracerProvider.forceFlush().join(5, TimeUnit.SECONDS)
            openTelemetry.sdkTracerProvider.shutdown()
        })
    }

    return openTelemetry
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
