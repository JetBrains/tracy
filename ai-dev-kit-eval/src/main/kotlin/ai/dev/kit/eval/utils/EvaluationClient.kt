package ai.dev.kit.eval.utils

import ai.dev.kit.tracing.fluent.dataclasses.RunStatus
import ai.dev.kit.tracing.fluent.dataclasses.TraceInfo

interface EvaluationClient {
    val clientName: String
    fun setupTracing()
    fun getOrCreateExperiment(experimentName: String): String
    fun createRun(experimentId: String, runName: String): String
    fun getResultsLink(experimentId: String, runId: String): String
    fun logMetric(runId: String, name: String, score: Double)
    fun uploadResultsTable(runId: String, table: org.jetbrains.kotlinx.dataframe.DataFrame<*>)
    fun applyTag(runId: String, tag: RunTag)
    fun changeRunStatus(runId: String, runStatus: RunStatus)
    fun uploadTraceStart(tracePostRequest: TracePostRequest): TraceInfo
}
