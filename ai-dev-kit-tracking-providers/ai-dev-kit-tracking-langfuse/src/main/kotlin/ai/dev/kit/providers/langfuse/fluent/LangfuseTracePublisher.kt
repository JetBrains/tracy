package ai.dev.kit.providers.langfuse.fluent

import ai.dev.kit.providers.langfuse.KotlinLangfuseClient
import ai.dev.kit.providers.langfuse.langfuseRequest
import ai.dev.kit.tracing.fluent.FluentSpanAttributes
import io.ktor.http.*
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.trace.ReadableSpan
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.*

private fun parseLenientJson(raw: String?): JsonElement? {
    return try {
        raw?.let { Json.parseToJsonElement(it) }
    } catch (_: Exception) {
        raw?.let { JsonPrimitive(it) }
    }
}

private fun prepareInputsOutputs(inputRaw: String, outputRaw: String?): Pair<JsonElement?, JsonElement?> {
    val inputs = parseLenientJson(inputRaw)

    val output = outputRaw?.let {
        val parsedOutput = parseLenientJson(it) ?: JsonNull
        if (parsedOutput is JsonObject) {
            val assistantMessage =
                parsedOutput.jsonObject["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject

            assistantMessage?.let {
                buildJsonObject {
                    put("role", it["role"] ?: JsonNull)
                    put("content", it["content"] ?: JsonNull)
                }
            } ?: JsonNull
        } else {
            parsedOutput
        }
    }


    return inputs to
            // This is an issue in Langfuse. {"output: 0"} is considered as {"output": null}, thus no output is shown.
            // TODO: create an issue in Langfuse repo or watch for updates
            if (output == JsonPrimitive(0)) {
                buildJsonObject { put("output", output) }
            } else {
                output
            }
}

private fun buildTraceCreateCall(
    startedAtMillis: Long,
    traceId: String,
    runId: String?,
    name: String,
    sourceName: String?,
    functionName: String?,
    inputRaw: String,
    tags: List<String>?,
    outputRaw: String? = null,
    type: String = "trace-create"
): JsonObject {
    val instantStart = Instant.ofEpochMilli(startedAtMillis)
    val startedAt = DateTimeFormatter.ISO_INSTANT.format(instantStart)
    val userId = KotlinLangfuseClient.USER_ID

    val (inputMessages, outputs) = prepareInputsOutputs(inputRaw, outputRaw)

    return buildJsonObject {
        put("id", UUID.randomUUID().toString())
        put("timestamp", startedAt)
        put("type", type)
        put("body", buildJsonObject {
            put("id", traceId)
            put("timestamp", startedAt)
            put("name", name)
            put("environment", "production")
            put("userId", userId)
            runId?.let { put("sessionId", it) }
            inputMessages?.let { put("input", it) }
            outputs?.let { put("output", it) }
            // traces from ai-dev-kit allways have tag "kotlin"
            put("tags", JsonArray((tags?.map { JsonPrimitive(it) } ?: emptyList()) + JsonPrimitive("kotlin")))

            put("metadata", buildJsonObject {
                sourceName?.let { put("sourceName", it) }
                functionName?.let { put("functionName", it) }
            })
        })
    }
}

internal suspend fun publishRootStartCall(span: ReadableSpan, runId: String? = null) {
    val traceId = span.spanContext.traceId
    val spanName = span.name
    val spanInputs = span.getAttribute(AttributeKey.stringKey(FluentSpanAttributes.SPAN_INPUTS.key)) ?: ""
    val sourceName = span.getAttribute(AttributeKey.stringKey(FluentSpanAttributes.SPAN_SOURCE_NAME.key)) ?: ""
    val name = span.getAttribute(AttributeKey.stringKey(FluentSpanAttributes.SPAN_FUNCTION_NAME.key)) ?: spanName
    val functionName = span.getAttribute(AttributeKey.stringKey(FluentSpanAttributes.SPAN_FUNCTION_NAME.key))
    val startedAtMillis = Instant.now().toEpochMilli()
    val tags = emptyList<String>()

    val traceCreateCall = buildTraceCreateCall(
        startedAtMillis, traceId, runId, name, sourceName, functionName, spanInputs, tags, null
    )

    val payload = buildJsonObject {
        put("batch", JsonArray(listOf(traceCreateCall)))
    }
    langfuseRequest(
        method = HttpMethod.Post, url = "${KotlinLangfuseClient.LANGFUSE_BASE_URL}/api/public/ingestion", body = payload
    )
}