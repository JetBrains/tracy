package ai.dev.kit.providers.mlflow

import ai.dev.kit.tracing.fluent.processor.TracePublisher
import ai.dev.kit.tracing.fluent.processor.TracingMetadataConfigurator
import ai.dev.kit.providers.mlflow.fluent.MlflowTracePublisher
import ai.dev.kit.providers.mlflow.fluent.MlflowTracingMetadataConfigurator
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton

internal object MlflowDiContainer {
    val di = DI {
        bind<TracingMetadataConfigurator>() with singleton { MlflowTracingMetadataConfigurator() }
        bind<TracePublisher>() with singleton { MlflowTracePublisher() }
    }
}
