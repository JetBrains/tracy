package ai.dev.kit.providers.langfuse

import ai.dev.kit.tracing.fluent.processor.TracePublisher
import ai.dev.kit.tracing.fluent.processor.TracingMetadataConfigurator
import ai.dev.kit.providers.langfuse.fluent.LangfuseTracePublisher
import ai.dev.kit.providers.langfuse.fluent.LangfuseTracingMetadataConfigurator
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton

internal object LangfuseDiContainer {
    val di = DI {
        bind<TracingMetadataConfigurator>() with singleton { LangfuseTracingMetadataConfigurator() }
        bind<TracePublisher>() with singleton { LangfuseTracePublisher() }
    }
}
