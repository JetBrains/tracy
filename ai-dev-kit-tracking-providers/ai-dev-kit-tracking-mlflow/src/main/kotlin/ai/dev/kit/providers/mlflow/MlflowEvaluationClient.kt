package ai.dev.kit.providers.mlflow

import ai.dev.kit.eval.utils.*
import ai.dev.kit.providers.mlflow.KotlinMlflowClient.ML_FLOW_URL
import ai.dev.kit.providers.mlflow.KotlinMlflowClient.currentExperimentId
import ai.dev.kit.providers.mlflow.KotlinMlflowClient.currentRunId
import ai.dev.kit.providers.mlflow.dataclasses.dumpForMLFlow
import ai.dev.kit.providers.mlflow.fluent.setupMlflowTracing
import ai.dev.kit.tracing.fluent.FluentSpanAttributes
import ai.dev.kit.tracing.fluent.dataclasses.RunStatus
import ai.dev.kit.tracing.fluent.dataclasses.TraceInfo
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import kotlinx.serialization.json.Json

object MlflowEvaluationClient : EvaluationClient {
    override val clientName: String = "Mlflow"

    override fun setupTracing() {
        setupMlflowTracing()
    }

    override suspend fun getOrCreateExperiment(experimentName: String): String {
        val experimentId = getExperimentByName(
            KotlinMlflowClient,
            experimentName
        )?.experimentId
            ?: createExperiment(
                KotlinMlflowClient,
                experimentName
            )
            ?: throw IllegalStateException("Failed to create or retrieve experiment '$experimentName' at $ML_FLOW_URL")
        currentExperimentId = experimentId

        return experimentId
    }

    override fun createRun(experimentId: String, runName: String): String {
        val runId = createRun(
            KotlinMlflowClient,
            runName,
            experimentId
        )?.runId.toString()

        currentRunId = runId
        return runId
    }

    override fun getResultsLink(experimentId: String, runId: String) =
        "$ML_FLOW_URL/#/experiments/$experimentId/runs/$runId"

    override suspend fun logMetric(runId: String, name: String, score: Double, traceId: String?) {
        logMlflowMetric(
            KotlinMlflowClient,
            runId,
            name,
            score
        )
    }

    override suspend fun uploadResults(runId: String, testResults: List<TestResult<*, *, *, *>>) {
        val table = testResults.toTable()

        val loggedRun = getRun(runId)
        val artifactPath = "${loggedRun.info.experimentId}/${runId}/artifacts/eval_results_table.json"
        uploadArtifact(artifactPath, table.dumpForMLFlow())

        setTag(
            runId,
            "mlflow.loggedArtifacts",
            "[{\"path\": \"eval_results_table.json\", \"type\": \"table\"}]"
        )
    }

    override suspend fun applyTag(runId: String, tag: RunTag) {
        setTag(
            runId,
            "mlflow.runColor",
            tag.color,
        )
    }

    override suspend fun changeRunStatus(runId: String, runStatus: RunStatus) {
        updateRun(runId, runStatus)
    }

    override suspend fun uploadTraceStart(
        experimentId: String,
        runId: String,
        spanBuilder: SpanBuilder,
        tracedRunName: String
    ): Span {
        val tracePostRequest = createTracePostRequest(
            experimentId = experimentId,
            runId = runId,
            traceCreationPath = "No path for root test",
            traceName = tracedRunName
        )
        val jsonTraceInfo = Json.encodeToString(TraceInfo.serializer(), createTrace(tracePostRequest))
        spanBuilder.setAttribute(FluentSpanAttributes.TRACE_CREATION_INFO.key, jsonTraceInfo)

        return spanBuilder.startSpan()
    }
}
