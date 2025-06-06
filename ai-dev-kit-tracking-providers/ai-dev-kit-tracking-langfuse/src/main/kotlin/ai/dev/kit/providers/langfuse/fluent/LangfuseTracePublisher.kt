package ai.dev.kit.providers.langfuse.fluent

import ai.dev.kit.providers.langfuse.KotlinLangfuseClient
import ai.dev.kit.providers.langfuse.langfuseRequest
import ai.dev.kit.tracing.fluent.FluentSpanAttributes
import ai.dev.kit.tracing.fluent.SpanType
import ai.dev.kit.tracing.fluent.getAttribute
import ai.dev.kit.tracing.fluent.processor.TracePublisher
import io.ktor.http.*
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.SpanId
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.data.SpanData
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.*

class LangfuseTracePublisher : TracePublisher {
    override suspend fun publishTrace(trace: List<SpanData>) {
        val rootSpan: SpanData = trace.find { it.parentSpanId == SpanId.getInvalid() }
            ?: throw IllegalStateException("Parent span not found.")

        val startEpochNanos = rootSpan.startEpochNanos / 1_000_000
        val traceId = rootSpan.traceId
        val runId = rootSpan.getAttribute(FluentSpanAttributes.SOURCE_RUN)
        val name = rootSpan.getAttribute(FluentSpanAttributes.SPAN_FUNCTION_NAME) ?: rootSpan.name
        val inputRaw = rootSpan.getAttribute(FluentSpanAttributes.SPAN_INPUTS) ?: ""
        val outputRaw = rootSpan.getAttribute(FluentSpanAttributes.SPAN_OUTPUTS) ?: ""
        val sourceName = rootSpan.getAttribute(FluentSpanAttributes.SPAN_SOURCE_NAME)
        val functionName = rootSpan.getAttribute(FluentSpanAttributes.SPAN_FUNCTION_NAME)

        val tags = trace.flatMap { span ->
            span.getAttribute(FluentSpanAttributes.TRACE_TAGS)
                ?.removePrefix("[")
                ?.removeSuffix("]")
                ?.split(",")
                ?.map { it.trim() }
                ?: emptyList()
        }

        val traceCreateCall = buildTraceCreateCall(
            startEpochNanos,
            traceId,
            runId,
            name,
            sourceName,
            functionName,
            inputRaw,
            tags,
            outputRaw,
            "trace-create"
        )

        val spanCreateCalls = trace.map { span ->
            buildSpanCreateCall(span)
        }

        val batch = listOf(traceCreateCall) + spanCreateCalls
        val payload = buildJsonObject {
            put("batch", JsonArray(batch))
        }
        langfuseRequest(
            method = HttpMethod.Post,
            url = "${KotlinLangfuseClient.LANGFUSE_BASE_URL}/api/public/ingestion",
            body = payload
        )
    }

    private fun buildSpanCreateCall(span: SpanData): JsonObject {
        val startTime = Instant.ofEpochMilli(span.startEpochNanos / 1_000_000)
        val endTime = Instant.ofEpochMilli(span.endEpochNanos / 1_000_000)
        val parentId =
            if (span.parentSpanId != SpanId.getInvalid()) JsonPrimitive(span.parentSpanId) else null

        val spanType = span.getAttribute(FluentSpanAttributes.SPAN_TYPE) ?: "UNKNOWN"
        val inputRaw = span.getAttribute(FluentSpanAttributes.SPAN_INPUTS) ?: ""
        val outputRaw = span.getAttribute(FluentSpanAttributes.SPAN_OUTPUTS) ?: ""
        val functionName = span.getAttribute(FluentSpanAttributes.SPAN_FUNCTION_NAME)

        val runId = span.getAttribute(FluentSpanAttributes.SOURCE_RUN)
        val (inputMessages, output) = prepareInputsOutputs(inputRaw, outputRaw)

        val isLLMType = spanType.equals(SpanType.LLM, ignoreCase = true)
        val hasModelKey = inputMessages is JsonObject && inputMessages.jsonObject.keys.contains("model")

        val type = if (isLLMType || hasModelKey) {
            "generation-create"
        } else {
            "span-create"
        }

        return buildJsonObject {
            put("id", UUID.randomUUID().toString())
            put("timestamp", startTime.toString())
            put("type", JsonPrimitive(type))
            put("body", buildJsonObject {
                put("id", span.spanId)
                put("name", span.name)
                put("traceId", span.traceId)
                put("startTime", startTime.toString())
                put("endTime", endTime.toString())
                runId?.let { put("sessionId", runId) }
                parentId?.let { put("parentObservationId", it) }
                inputMessages?.let { put("input", it) }
                put("metadata", buildJsonObject {
                    put("spanType", spanType)
                    functionName?.let { put("functionName", it) }
                })
                output?.let { put("output", it) }
            })
        }
    }

    companion object {
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
                    val assistantMessage = parsedOutput.jsonObject["choices"]
                        ?.jsonArray
                        ?.firstOrNull()
                        ?.jsonObject
                        ?.get("message")
                        ?.jsonObject

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
                    put(
                        "tags",
                        JsonArray(
                            (tags?.map { JsonPrimitive(it) } ?: emptyList()) + JsonPrimitive("kotlin")
                        )
                    )

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
            val name =
                span.getAttribute(AttributeKey.stringKey(FluentSpanAttributes.SPAN_FUNCTION_NAME.key)) ?: spanName
            val functionName =
                span.getAttribute(AttributeKey.stringKey(FluentSpanAttributes.SPAN_FUNCTION_NAME.key))
            val startedAtMillis = Instant.now().toEpochMilli()
            val tags = emptyList<String>()

            val traceCreateCall = buildTraceCreateCall(
                startedAtMillis,
                traceId,
                runId,
                name,
                sourceName,
                functionName,
                spanInputs,
                tags,
                null
            )

            val payload = buildJsonObject {
                put("batch", JsonArray(listOf(traceCreateCall)))
            }
            langfuseRequest(
                method = HttpMethod.Post,
                url = "${KotlinLangfuseClient.LANGFUSE_BASE_URL}/api/public/ingestion",
                body = payload
            )
        }
    }
}