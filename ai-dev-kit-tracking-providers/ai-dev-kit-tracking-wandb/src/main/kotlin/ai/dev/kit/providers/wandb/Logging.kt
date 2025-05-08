package ai.dev.kit.providers.wandb

import ai.dev.kit.tracing.fluent.dataclasses.RequestMetadata
import ai.dev.kit.tracing.fluent.dataclasses.Tag
import ai.dev.kit.tracing.fluent.dataclasses.TraceInfo
import ai.dev.kit.tracing.fluent.dataclasses.TracesResponse
import ai.dev.kit.providers.wandb.KotlinWandbClient.USER_ID
import ai.dev.kit.providers.wandb.KotlinWandbClient.WANDB_USER_API_KEY
import ai.dev.kit.providers.wandb.dataclasses.Call
import ai.dev.kit.providers.wandb.dataclasses.DeleteCallsRequest
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import java.time.Duration
import java.time.Instant

val json = Json {
    ignoreUnknownKeys = true
}

private suspend fun postJson(url: String, payload: JsonObject): JsonObject {
    val response: HttpResponse = KotlinWandbClient.client.post(url) {
        contentType(ContentType.Application.Json)
        headers { append(HttpHeaders.Authorization, WANDB_USER_API_KEY) }
        setBody(payload)
    }

    return Json.parseToJsonElement(response.bodyAsText()).jsonObject
}

private fun makeHierarchy(list: JsonArray): Map<String, List<String>> =
    list.mapNotNull {
        it.jsonObject["parent_id"].toString().trim('"') to it.jsonObject["id"].toString().trim('"')
    }.groupBy({ it.first }, { it.second })

private suspend fun getTraceIds(projectId: String): JsonArray {
    val payload = buildJsonObject {
        put("project_id", "$USER_ID/$projectId")
        putJsonArray("columns") {
            add("trace_id")
            add("parent_id")
        }
    }

    val calls =
        postJson("https://trace.wandb.ai/calls/query", payload)["calls"]?.jsonArray ?: return JsonArray(emptyList())

    return calls
}

private suspend fun getTraceDetails(projectId: String, spanId: String): JsonObject {
    val payload = buildJsonObject {
        put("project_id", projectId)
        put("id", spanId)
    }

    return postJson("https://trace.wandb.ai/call/read", payload)
}

/**
 * Builds a [TraceInfo] object from a list of span JSON objects.
 * Converts the span information into a `TraceInfo` format matching the test requirements.
 *
 * @param spanObjects A list of JSON objects representing trace spans.
 * @return A [TraceInfo] object with extracted details adjusted for tests.
 */

private fun buildTraceInfo(spanObjects: List<JsonObject>): TraceInfo? {
    val rootSpan = spanObjects.firstOrNull {
        it["call"]?.jsonObject?.get("parent_id") == JsonNull
    } ?: spanObjects.first()

    val call = rootSpan["call"]?.let { json.decodeFromJsonElement<Call>(it) } ?: return null
    val requestId = call.traceId
    val projectId = call.projectId
    val sourceName = call.attributes.spanSource ?: ""
    val experimentId = projectId.substringAfterLast("/")

    val startedAt = Instant.parse(call.startedAt)
    val endedAt = Instant.parse(call.endedAt)

    val timestampMs = startedAt.toEpochMilli()
    val executionTimeMs = Duration.between(startedAt, endedAt).toMillis().toInt()

    val status = call.summary.status

    val output =
        when (val outputElement = call.output) {
            is JsonPrimitive -> outputElement.contentOrNull
            else -> outputElement.jsonObject["choices"]
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?.get("message")
                ?.toString()
        } ?: ""


    val inputs = extractInput(call.inputs)

    val requestMetadata = buildList {
        add(RequestMetadata("trace_schema.version", "2"))
        add(RequestMetadata("traceInputs", inputs))
        add(RequestMetadata("traceOutputs", output))
    }

    val traceSpans = spanObjects.reversed().mapNotNull { span ->
        val c = span["call"]?.jsonObject ?: return@mapNotNull null
        val name = c["display_name"]?.jsonPrimitive ?: return@mapNotNull null

        val inputsStr = extractInput(c["inputs"]?.jsonObject)
            .replace("\"", "\\\"")

        val type = c["attributes"]?.jsonObject?.get("spanType")?.jsonPrimitive
        """{"name":$name,"type":$type,"inputs":"$inputsStr"}"""
    }

    val tags = listOf(
        Tag("source.name", sourceName),
        Tag("traceSpans", traceSpans.joinToString(separator = ",", prefix = "[", postfix = "]")),
        Tag("source.type", "LOCAL"),
        Tag("traceName", call.displayName)
    )

    return TraceInfo(
        requestId = requestId,
        experimentId = experimentId,
        timestampMs = timestampMs,
        executionTimeMs = executionTimeMs,
        status = status,
        requestMetadata = requestMetadata,
        tags = tags
    )
}

private fun extractInput(input: JsonObject?): String {
    if (input == null) return "{}"

    return when {
        input["model"] != JsonNull || input["temperature"] != JsonNull -> input.toString()
        else -> input["messages"]?.jsonArray
            ?.firstOrNull()?.jsonObject
            ?.get("content")?.jsonPrimitive?.contentOrNull ?: "{}"
    }
}

internal suspend fun getAllTracesForProject(projectId: String): TracesResponse {
    val traceIds = getTraceIds(projectId)
    val spanBranches = getAllBranches(makeHierarchy(traceIds))

    val traces = spanBranches.mapNotNull { spanIds ->
        val spans = spanIds.map {
            getTraceDetails("$USER_ID/$projectId", it)
        }
        runCatching { buildTraceInfo(spans) }.getOrNull()
    }

    return TracesResponse(traces)
}

private fun getAllBranches(map: Map<String, List<String>>): List<List<String>> {
    val result = mutableListOf<List<String>>()

    fun visitBranch(parentId: String, currentBranch: MutableList<String>) {
        currentBranch += parentId
        map[parentId]?.forEach { childId ->
            visitBranch(childId, currentBranch)
        }
    }

    map["null"]?.forEach { root ->
        val branch = mutableListOf<String>()
        visitBranch(root, branch)
        result += branch
    }

    return result
}

internal suspend fun deleteAllTracesFromProject(projectId: String): JsonObject {
    val projectTraces = getTraceIds(projectId)
    val callIds = projectTraces.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content }.distinct()

    val payload = DeleteCallsRequest(
        projectId = "$USER_ID/$projectId",
        callIds = callIds
    )

    val response: HttpResponse = KotlinWandbClient.client.post("https://trace.wandb.ai/calls/delete") {
        contentType(ContentType.Application.Json)
        headers { append(HttpHeaders.Authorization, WANDB_USER_API_KEY) }
        setBody(json.encodeToJsonElement(payload))
    }

    return Json.parseToJsonElement(response.bodyAsText()).jsonObject
}
