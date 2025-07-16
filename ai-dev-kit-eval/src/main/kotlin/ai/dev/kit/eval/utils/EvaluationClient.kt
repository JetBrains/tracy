package ai.dev.kit.eval.utils

import ai.dev.kit.tracing.fluent.dataclasses.RunStatus
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.sdk.trace.SdkTracerProvider

interface EvaluationClient {
    val clientName: String
    fun createRun(experimentId: String, runName: String): String
    fun getResultsLink(experimentId: String, runId: String): String
    suspend fun applyTag(runId: String, tag: RunTag)
    suspend fun changeRunStatus(runId: String, runStatus: RunStatus)
    suspend fun getOrCreateExperiment(experimentName: String): String?
    suspend fun logMetric(runId: String, name: String, score: Double, traceId: String? = null)
    suspend fun uploadResults(runId: String, testResults: List<TestResult<*, *, *, *>>)
    suspend fun uploadTraceStart(experimentId: String, runId: String, spanBuilder: SpanBuilder, tracedRunName: String): Span
}
