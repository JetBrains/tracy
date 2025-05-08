package ai.dev.kit.providers.langfuse

import ai.dev.kit.tracing.fluent.dataclasses.RunStatus
import ai.dev.kit.tracing.fluent.dataclasses.TraceInfo
import ai.dev.kit.eval.utils.EvaluationClient
import ai.dev.kit.eval.utils.RunTag
import ai.dev.kit.eval.utils.TracePostRequest
import ai.dev.kit.providers.langfuse.fluent.setupLangfuseTracing
import org.jetbrains.kotlinx.dataframe.DataFrame

object LangfuseEvaluationClient : EvaluationClient {
    override val clientName: String = "Langfuse"

    override fun setupTracing() {
        setupLangfuseTracing()
    }

    override fun getOrCreateExperiment(experimentName: String): String {
        TODO("Not yet implemented")
    }

    override fun createRun(experimentId: String, runName: String): String {
        TODO("Not yet implemented")
    }

    override fun getResultsLink(experimentId: String, runId: String): String {
        TODO("Not yet implemented")
    }

    override fun logMetric(runId: String, name: String, score: Double) {
        TODO("Not yet implemented")
    }

    override fun uploadResultsTable(runId: String, table: DataFrame<*>) {
        TODO("Not yet implemented")
    }

    override fun applyTag(runId: String, tag: RunTag) {
        TODO("Not yet implemented")
    }

    override fun changeRunStatus(runId: String, runStatus: RunStatus) {
        TODO("Not yet implemented")
    }

    override fun uploadTraceStart(tracePostRequest: TracePostRequest): TraceInfo {
        TODO("Not yet implemented")
    }
}