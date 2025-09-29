package ai.dev.kit.eval.providers.langfuse

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import java.util.*

private val langfuseJson = Json { ignoreUnknownKeys = true }

internal suspend fun langfuseRequest(
    method: HttpMethod,
    url: String,
    body: JsonElement? = null
): JsonObject {
    val response = KotlinLangfuseClient.client.request(url) {
        this.method = method
        contentType(ContentType.Application.Json)
        headers {
            append(
                HttpHeaders.Authorization, "Basic " + Base64.getEncoder().encodeToString(
                    "${KotlinLangfuseClient.LANGFUSE_PUBLIC_KEY}:${KotlinLangfuseClient.LANGFUSE_SECRET_KEY}".toByteArray()
                )
            )
        }
        body?.let { setBody(it) }
    }

    return langfuseJson.parseToJsonElement(response.bodyAsText()).jsonObject
}

suspend fun getLangfuseProject(): JsonArray {
    val response = langfuseRequest(
        method = HttpMethod.Get,
        url = "${KotlinLangfuseClient.LANGFUSE_BASE_URL}/api/public/projects"
    )

    return response["data"]?.jsonArray
        ?: error("Projects not found in response.")
}

suspend fun logScoreToLangfuse(
    traceId: String? = null,
    sessionId: String? = null,
    observationId: String?,
    name: String,
    value: Double,
    comment: String? = null,
    configId: String? = null,
    dataType: LangfuseEvaluationClient.Companion.LangfuseMetricDataType
) {
    require((traceId == null) xor (sessionId == null)) { "Exactly one of `sessionId` and `traceId` must be not null" }

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

    langfuseRequest(
        method = HttpMethod.Post,
        url = "${KotlinLangfuseClient.LANGFUSE_BASE_URL}/api/public/scores",
        body = payload
    )
}
