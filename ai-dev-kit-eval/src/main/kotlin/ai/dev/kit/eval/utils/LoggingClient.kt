package ai.dev.kit.eval.utils

import ai.dev.kit.tracing.fluent.dataclasses.RunStatus

interface LoggingClient {
    val clientName: String
    fun createRun(experimentId: String, runName: String): String
    fun getTraceLink(experimentId: String, traceId: String): String
    fun getRunLink(experimentId: String, runId: String): String
    suspend fun applyTag(runId: String, tag: RunTag)
    suspend fun changeRunStatus(runId: String, runStatus: RunStatus)
    suspend fun getOrCreateExperiment(experimentName: String): String?
    suspend fun logMetric(runId: String, name: String, score: Double, traceId: String? = null)
    suspend fun uploadResults(runId: String, testResults: List<TestResult<*, *, *, *>>)
}
