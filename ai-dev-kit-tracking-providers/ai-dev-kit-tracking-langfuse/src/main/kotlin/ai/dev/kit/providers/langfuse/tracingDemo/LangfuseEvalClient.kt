package ai.dev.kit.providers.langfuse.tracingDemo

import ai.dev.kit.eval.utils.tracingDemo.EvalClient
import ai.dev.kit.providers.langfuse.LangfuseDiContainer
import ai.dev.kit.providers.langfuse.fluent.LangfuseTracePublisher.Companion.publishRootStartCall
import io.opentelemetry.sdk.trace.ReadableSpan

class LangfuseEvalClient : EvalClient {
    override val di = LangfuseDiContainer.di
    override suspend fun publishStartCall(span: ReadableSpan, runId: String, traceName: String) {
        publishRootStartCall(
            span
        )
    }
}
