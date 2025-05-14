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
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

object MlflowEvaluationClient : EvaluationClient {
    override val clientName: String = "Mlflow"

    override fun setupTracing() {
        setupMlflowTracing()
    }

    override fun getOrCreateExperiment(experimentName: String): String {
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

    override fun logMetric(runId: String, name: String, score: Double, traceId: String?) {
        logMlflowMetric(
            KotlinMlflowClient,
            runId,
            name,
            score
        )
    }

    override fun uploadResults(runId: String, testResults: List<TestResult<*, *, *, *>>) {
        val table = testResults.toTable()

        val loggedRun = runBlocking { getRun(runId) }
        val artifactPath = "${loggedRun.info.experimentId}/${runId}/artifacts/eval_results_table.json"
        uploadArtifact(artifactPath, table.dumpForMLFlow())

        runBlocking {
            setTag(
                runId,
                "mlflow.loggedArtifacts",
                "[{\"path\": \"eval_results_table.json\", \"type\": \"table\"}]"
            )
        }
    }

    override fun applyTag(runId: String, tag: RunTag) {
        runBlocking {
            setTag(
                runId,
                "mlflow.runColor",
                tag.color,
            )
        }
    }

    override fun changeRunStatus(runId: String, runStatus: RunStatus) {
        runBlocking { updateRun(runId, runStatus) }
    }

    override fun uploadTraceStart(
        experimentId: String,
        runId: String,
        spanBuilder: SpanBuilder,
        tracedRunName: String
    ): Span = runBlocking {
        val tracePostRequest = createTracePostRequest(
            experimentId = experimentId,
            runId = runId,
            traceCreationPath = "No path for root test",
            traceName = tracedRunName
        )
        val jsonTraceInfo = Json.encodeToString(TraceInfo.serializer(), createTrace(tracePostRequest))
        spanBuilder.setAttribute(FluentSpanAttributes.TRACE_CREATION_INFO.key, jsonTraceInfo)

        return@runBlocking spanBuilder.startSpan()
    }
}