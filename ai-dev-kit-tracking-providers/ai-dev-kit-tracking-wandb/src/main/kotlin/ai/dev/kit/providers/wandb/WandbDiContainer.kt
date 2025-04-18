package ai.dev.kit.providers.wandb

import ai.dev.kit.core.fluent.processor.TracePublisher
import ai.dev.kit.core.fluent.processor.TracingMetadataConfigurator
import ai.dev.kit.providers.wandb.fluent.WandbTracePublisher
import ai.dev.kit.providers.wandb.fluent.WandbTracingMetadataConfigurator
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton

internal object WandbDiContainer {
    val di = DI {
        bind<TracingMetadataConfigurator>() with singleton { WandbTracingMetadataConfigurator() }
        bind<TracePublisher>() with singleton { WandbTracePublisher() }
    }
}
