package ai.dev.kit.providers.wandb.tracingDemo

import ai.dev.kit.eval.utils.tracingDemo.EvalClient
import ai.dev.kit.providers.wandb.WandbDiContainer
import ai.dev.kit.providers.wandb.fluent.WandbTracePublisher.Companion.publishRootStartCall
import io.opentelemetry.sdk.trace.ReadableSpan

class WandbEvalClient : EvalClient {
    override val di = WandbDiContainer.di
    override suspend fun publishStartCall(span: ReadableSpan, runId: String, traceName: String) {
        publishRootStartCall(
            span
        )
    }
}
