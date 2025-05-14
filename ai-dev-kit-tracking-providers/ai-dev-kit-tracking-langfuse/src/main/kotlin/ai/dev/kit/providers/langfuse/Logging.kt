package ai.dev.kit.providers.langfuse

import ai.dev.kit.tracing.fluent.dataclasses.RequestMetadata
import ai.dev.kit.tracing.fluent.dataclasses.Tag
import ai.dev.kit.tracing.fluent.dataclasses.TraceInfo
import ai.dev.kit.tracing.fluent.dataclasses.TracesResponse
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import java.time.Duration
import java.time.Instant
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

private suspend fun getTraceIds(projectId: String): List<String> { // TODO: decide how to understand how to take only needed traces
    val json = langfuseRequest(
        method = HttpMethod.Get,
        url = "${KotlinLangfuseClient.LANGFUSE_BASE_URL}api/public/traces?limit=1"
    )

    return json["data"]?.jsonArray?.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull }
        ?: return emptyList()
}

private suspend fun getSpansForTrace(traceId: String): List<JsonObject> {
    val trace = langfuseRequest(
        method = HttpMethod.Get,
        url = "${KotlinLangfuseClient.LANGFUSE_BASE_URL}api/public/traces/$traceId"
    )

    val spanIds = trace["observations"]?.jsonArray?.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull }
        ?: return listOf(trace)

    val spans = spanIds.mapNotNull { obsId ->
        runCatching {
            langfuseRequest(
                method = HttpMethod.Get,
                url = "${KotlinLangfuseClient.LANGFUSE_BASE_URL}api/public/observations/$obsId"
            )
        }.getOrNull()
    }

    return spans
}

private fun buildTraceInfo(spanObjects: List<JsonObject>): TraceInfo? {
    val rootSpan = spanObjects.firstOrNull {
        it["parentObservationId"] == JsonNull
    } ?: spanObjects.first()

    val traceId = rootSpan["id"]?.jsonPrimitive?.contentOrNull ?: return null
    val projectId = rootSpan["projectId"]?.jsonPrimitive?.contentOrNull ?: "unknown-project"
    val name = rootSpan["name"]?.jsonPrimitive?.contentOrNull ?: "Unnamed Trace"
    val timestamp = rootSpan["timestamp"]?.jsonPrimitive?.contentOrNull?.let { Instant.parse(it) } ?: Instant.now()
    val endTime = rootSpan["endTime"]?.jsonPrimitive?.contentOrNull?.let { Instant.parse(it) } ?: timestamp
    val executionTimeMs = Duration.between(timestamp, endTime).toMillis().toInt()

    val input = extractInput(rootSpan["input"])
    val output = extractOutput(rootSpan["output"])

    val sourceName = rootSpan["metadata"]?.jsonObject?.get("sourceName")?.jsonPrimitive?.content ?: "UNKNOWN"

    val requestMetadata = listOf(
        RequestMetadata("trace_schema.version", "2"),
        RequestMetadata("traceInputs", input),
        RequestMetadata("traceOutputs", output)
    )

    val traceSpans = spanObjects.reversed().mapNotNull { span ->
        val spanName = span["name"]?.jsonPrimitive ?: return@mapNotNull null
        val type = span["metadata"]?.jsonObject?.get("spanType")?.jsonPrimitive ?: "UNKNOWN"
        val inputsStr = extractInput(span["input"]).replace("\"", "\\\"")
        """{"name":$spanName,"type":$type,"inputs":"$inputsStr"}"""
    }

    val tags = listOf(
        Tag("source.type", sourceName),
        Tag("traceSpans", traceSpans.joinToString(",", "[", "]")),
        Tag("traceName", name)
    )

    return TraceInfo(
        requestId = traceId,
        experimentId = projectId,
        timestampMs = timestamp.toEpochMilli(),
        executionTimeMs = executionTimeMs,
        status = "OK",
        requestMetadata = requestMetadata,
        tags = tags
    )
}

private fun extractInput(input: JsonElement?): String {
    if (input == null || input is JsonNull) return "{}"
    return when (input) {
        is JsonObject -> {
            if (input["model"] != JsonNull || input["temperature"] != JsonNull) {
                input.toString()
            } else {
                input["messages"]?.jsonArray
                    ?.firstOrNull()?.jsonObject
                    ?.get("content")?.jsonPrimitive?.contentOrNull ?: "{}"
            }
        }

        is JsonPrimitive -> input.contentOrNull ?: "{}"
        else -> "{}"
    }
}

private fun extractOutput(output: JsonElement?): String {
    if (output == null || output is JsonNull) return ""
    return when (output) {
        is JsonPrimitive -> output.contentOrNull ?: ""
        is JsonObject -> output["choices"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("message")
            ?.toString() ?: ""

        else -> ""
    }
}

internal suspend fun getAllTracesForProject(projectId: String): TracesResponse {
    val traceIds = getTraceIds(projectId)

    val traces = traceIds.mapNotNull { traceId ->
        val spans = sortSpansByHierarchy(getSpansForTrace(traceId))
        runCatching { buildTraceInfo(spans) }.getOrNull()
    }

    return TracesResponse(traces)
}

private fun sortSpansByHierarchy(spans: List<JsonObject>): List<JsonObject> {
    val parentToChildren = spans.groupBy { it["parentObservationId"]?.jsonPrimitive?.contentOrNull }
    val result = mutableListOf<JsonObject>()

    fun visit(parentId: String?) {
        parentToChildren[parentId]?.forEach {
            result += it
            visit(it["id"]?.jsonPrimitive?.content)
        }
    }

    visit(null)
    return result
}


private suspend fun createLangfuseProject(
    projectId: String,
    projectName: String
): JsonObject { // TODO: add proper tokens
    val payload = buildJsonObject {
        put("id", projectId)
        put("name", projectName)
    }

    val response = langfuseRequest(
        method = HttpMethod.Post,
        url = "${KotlinLangfuseClient.LANGFUSE_BASE_URL}api/public/projects",
        body = payload
    )

    return response
}

private suspend fun deleteLangfuseTrace(traceId: String): JsonObject {
    val response = langfuseRequest(
        method = HttpMethod.Delete,
        url = "${KotlinLangfuseClient.LANGFUSE_BASE_URL}api/public/traces/$traceId"
    )

    return response
}

suspend fun getLangfuseProject(): JsonArray {
    val response = langfuseRequest(
        method = HttpMethod.Get,
        url = "${KotlinLangfuseClient.LANGFUSE_BASE_URL}api/public/projects"
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
    dataType: LangfuseEvaluationClient.LangfuseMetricDataType
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
        url = "${KotlinLangfuseClient.LANGFUSE_BASE_URL}api/public/scores",
        body = payload
    )
}