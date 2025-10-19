package ai.dev.kit.eval.providers.langfuse

import ai.dev.kit.eval.providers.dataclasses.RunStatus
import ai.dev.kit.eval.providers.langfuse.KotlinLangfuseClient.baseUrl
import ai.dev.kit.eval.utils.*
import ai.dev.kit.tracing.LangfuseConfig
import ai.dev.kit.tracing.TracingManager
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class LangfuseEvaluationClient(
    langfuseConfig: LangfuseConfig
) : LoggingClient {
    override val clientName: String = "Langfuse"

    init {
        KotlinLangfuseClient.setupCredentials(
            langfuseConfig.langfuseUrl,
            langfuseConfig.langfusePublicKey,
            langfuseConfig.langfuseSecretKey
        )
        TracingManager.setup(langfuseConfig)
    }

    override suspend fun getOrCreateExperiment(experimentName: String): String? {
        val resp = getLangfuseProject()
        return resp.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.content
    }

    // session in Langfuse
    override fun createRun(experimentId: String, runName: String): String = runName

    override fun getRunLink(experimentId: String, runId: String): String {
        return "$baseUrl/project/$experimentId/sessions/${
            URLEncoder.encode(
                runId,
                StandardCharsets.UTF_8.toString()
            )
        }"
    }

    override fun getTraceLink(experimentId: String, traceId: String): String {
        return "$baseUrl/project/$experimentId/traces/${
            URLEncoder.encode(
                traceId,
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
                    // TODO: improve
                    throw IllegalArgumentException("Unsupported EvalResult type: ${evalResult::class.simpleName}. For now use SingleScoreEvalResult or MultiScoreEvalResult")
                }
            }
        }
    }

    companion object {
        enum class LangfuseMetricDataType(val type: String) {
            NUMERIC("NUMERIC"),
            BOOLEAN("BOOLEAN"),
            CATEGORICAL("CATEGORICAL");
        }
    }
}
