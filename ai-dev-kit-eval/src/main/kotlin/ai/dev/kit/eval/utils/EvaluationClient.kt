package ai.dev.kit.eval.utils

import ai.dev.kit.tracing.fluent.dataclasses.RunStatus
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder

interface EvaluationClient {
    val clientName: String
    fun setupTracing()
    fun getOrCreateExperiment(experimentName: String): String?
    fun createRun(experimentId: String, runName: String): String
    fun getResultsLink(experimentId: String, runId: String): String
    fun logMetric(runId: String, name: String, score: Double, traceId: String? = null)
    fun uploadResults(runId: String, testResults: List<TestResult<*, *, *, *>>)
    fun applyTag(runId: String, tag: RunTag)
    fun changeRunStatus(runId: String, runStatus: RunStatus)
    fun uploadTraceStart(experimentId: String, runId: String, spanBuilder: SpanBuilder, tracedRunName: String): Span
}
