package ai.dev.kit.eval.utils.tracingDemo

import io.opentelemetry.sdk.trace.ReadableSpan
import org.kodein.di.DI

interface EvalClient {
    val di: DI
    suspend fun publishStartCall(span: ReadableSpan, runId: String, traceName: String)
}