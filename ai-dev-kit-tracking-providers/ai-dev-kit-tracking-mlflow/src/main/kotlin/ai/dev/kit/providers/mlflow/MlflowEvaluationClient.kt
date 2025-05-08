package ai.dev.kit.providers.mlflow

import ai.dev.kit.tracing.fluent.dataclasses.RunStatus
import ai.dev.kit.tracing.fluent.dataclasses.TraceInfo
import ai.dev.kit.eval.utils.EvaluationClient
import ai.dev.kit.eval.utils.RunTag
import ai.dev.kit.eval.utils.TracePostRequest
import ai.dev.kit.providers.mlflow.KotlinMlflowClient.ML_FLOW_URL
import ai.dev.kit.providers.mlflow.KotlinMlflowClient.currentExperimentId
import ai.dev.kit.providers.mlflow.KotlinMlflowClient.currentRunId
import ai.dev.kit.providers.mlflow.dataclasses.dumpForMLFlow
import ai.dev.kit.providers.mlflow.fluent.setupMlflowTracing
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlinx.dataframe.DataFrame

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

    override fun logMetric(runId: String, name: String, score: Double) {
        logMetric(
            KotlinMlflowClient,
            runId,
            name,
            score
        )
    }

    override fun uploadResultsTable(runId: String, table: DataFrame<*>) {
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

    override fun uploadTraceStart(tracePostRequest: TracePostRequest): TraceInfo = runBlocking {
        createTrace(tracePostRequest)
    }
}