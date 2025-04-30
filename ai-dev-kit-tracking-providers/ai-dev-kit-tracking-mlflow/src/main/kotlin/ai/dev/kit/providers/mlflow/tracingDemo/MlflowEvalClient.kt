package ai.dev.kit.providers.mlflow.tracingDemo

import ai.dev.kit.core.fluent.FluentSpanAttributes
import ai.dev.kit.core.fluent.dataclasses.TraceInfo
import ai.dev.kit.core.fluent.processor.Span
import ai.dev.kit.eval.utils.tracingDemo.EvalClient
import ai.dev.kit.providers.mlflow.KotlinMlflowClient
import ai.dev.kit.providers.mlflow.MlflowDiContainer
import ai.dev.kit.providers.mlflow.createTrace
import ai.dev.kit.providers.mlflow.dataclasses.createTracePostRequest
import io.opentelemetry.sdk.trace.ReadableSpan
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

class MlflowEvalClient : EvalClient {
    override val di = MlflowDiContainer.di
    override suspend fun publishStartCall(span: ReadableSpan, runId: String, traceName: String) {
        val experimentId = KotlinMlflowClient.currentExperimentId

        runBlocking {
            val tracePostRequest = createTracePostRequest(
                experimentId = experimentId,
                runId = runId,
                traceCreationPath = "No path for root test",
                traceName = traceName
            )

            val jsonTraceInfo = Json.encodeToString(TraceInfo.serializer(), createTrace(tracePostRequest))
            (span as Span).setAttribute(FluentSpanAttributes.TRACE_CREATION_INFO.key, jsonTraceInfo)
        }
    }
}
