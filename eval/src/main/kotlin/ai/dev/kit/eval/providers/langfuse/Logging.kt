package ai.dev.kit.eval.providers.langfuse

import io.ktor.http.*
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put

internal suspend fun KotlinLangfuseClient.getLangfuseProject(): JsonArray {
    val response = sendRequest(
        method = HttpMethod.Get,
        url = "$baseUrl/api/public/projects"
    )
    return response["data"]?.jsonArray ?: error("Projects not found in response")
}

internal suspend fun KotlinLangfuseClient.logScoreToLangfuse(
    traceId: String? = null,
    sessionId: String? = null,
    observationId: String?,
    name: String,
    value: Double,
    comment: String? = null,
    configId: String? = null,
    dataType: LangfuseEvaluationClient.Companion.LangfuseMetricDataType
) {
    require((traceId == null) xor (sessionId == null)) {
        "Exactly one of `sessionId` and `traceId` must be not null"
    }

    val payload = buildJsonObject {
        traceId?.let { put("traceId", it) }
        sessionId?.let { put("sessionId", it) }
        observationId?.let { put("observationId", it) }
        put("name", name)
        put("value", value)
        put("dataType", dataType.type)
        comment?.let { put("comment", it) }
        configId?.let { put("configId", it) }
    }

    sendRequest(
        method = HttpMethod.Post,
        url = "$baseUrl/api/public/scores",
        body = payload
    )
}
