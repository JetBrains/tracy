package ai.dev.kit.providers.langfuse

import ai.dev.kit.eval.utils.*
import ai.dev.kit.providers.langfuse.KotlinLangfuseClient.LANGFUSE_BASE_URL
import ai.dev.kit.providers.langfuse.KotlinLangfuseClient.currentRunId
import ai.dev.kit.providers.langfuse.fluent.LangfuseTracePublisher.Companion.publishRootStartCall
import ai.dev.kit.providers.langfuse.fluent.setupLangfuseTracing
import ai.dev.kit.tracing.fluent.dataclasses.RunStatus
import ai.dev.kit.tracing.fluent.processor.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.sdk.trace.ReadableSpan
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object LangfuseEvaluationClient : EvaluationClient {
    override val clientName: String = "Langfuse"

    override fun setupTracing() {
        setupLangfuseTracing()
    }

    override suspend fun getOrCreateExperiment(experimentName: String): String? {
        val resp = getLangfuseProject()
        // use createLangfuseProject() after TODO is completed
        return resp.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.content
    }

    // session in Langfuse
    override fun createRun(experimentId: String, runName: String): String {
        currentRunId = runName
        return runName
    }

    override fun getResultsLink(experimentId: String, runId: String): String {
        return "$LANGFUSE_BASE_URL/project/$experimentId/sessions/${
            URLEncoder.encode(
                runId,
                StandardCharsets.UTF_8.toString()
            )
        }"
    }

    override suspend fun logMetric(runId: String, name: String, score: Double, traceId: String?) {
        logScoreToLangfuse(
            traceId = traceId,
            sessionId = if (traceId == null) runId else null,
            observationId = null,
            name = name,
            value = score,
            comment = null,
            configId = null,
            dataType = LangfuseMetricDataType.NUMERIC
        )
    }

    override suspend fun uploadResults(runId: String, testResults: List<TestResult<*, *, *, *>>) {
        testResults.forEach { result ->
            when (val evalResult = result.evalResult) {
                is MultiScoreEvalResult -> {
                    evalResult.scores.forEach { score ->
                        logMetric(runId, score.scoreName, score.score.toDouble(), result.traceId)
                    }
                }

                is SingleScoreEvalResult -> {
                    logMetric(runId, evalResult.scoreName, evalResult.score.toDouble(), result.traceId)
                }

                else -> {
                    // Now supports only SingleScoreEvalResult and MultiScoreEvalResult similarly to ToTable()
                    // TODO: decide how to make it better
                    throw IllegalArgumentException("Unsupported EvalResult type: ${evalResult::class.simpleName}. For now use SingleScoreEvalResult or MultiScoreEvalResult")
                }
            }
        }
    }

    override suspend fun applyTag(runId: String, tag: RunTag) {
        // No tags in current Langfuse support
    }

    override suspend fun changeRunStatus(runId: String, runStatus: RunStatus) {
        // No Run status in Langfuse
    }

    override suspend fun uploadTraceStart(
        experimentId: String,
        runId: String,
        spanBuilder: SpanBuilder,
        tracedRunName: String
    ): Span {
            val span = spanBuilder.startSpan()
            publishRootStartCall(
                span as ReadableSpan,
                runId
            )
            return span
        }

    enum class LangfuseMetricDataType(val type: String) {
        NUMERIC("NUMERIC"),
        BOOLEAN("BOOLEAN"),
        CATEGORICAL("CATEGORICAL");
    }
}
