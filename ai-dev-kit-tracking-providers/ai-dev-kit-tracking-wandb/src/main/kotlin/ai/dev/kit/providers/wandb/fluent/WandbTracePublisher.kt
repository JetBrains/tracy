package ai.dev.kit.providers.wandb.fluent

import ai.dev.kit.providers.wandb.KotlinWandbClient
import ai.dev.kit.providers.wandb.KotlinWandbClient.USER_ID
import ai.dev.kit.providers.wandb.KotlinWandbClient.WANDB_API
import ai.dev.kit.providers.wandb.KotlinWandbClient.WANDB_USER_API_KEY
import ai.dev.kit.tracing.fluent.FluentSpanAttributes
import ai.dev.kit.tracing.fluent.TracingSessionProvider
import ai.dev.kit.tracing.fluent.getAttribute
import ai.dev.kit.tracing.fluent.processor.TracePublisher
import io.ktor.client.request.*
import io.ktor.http.*
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.SpanId
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.data.SpanData
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.format.DateTimeFormatter

class WandbTracePublisher : TracePublisher {
    private fun buildEndCall(
        currentExperimentId: String,
        spanId: String,
        endedAtMillis: Long,
        outputsString: String,
    ): JsonObject {
        val projectId = "$USER_ID/$currentExperimentId"

        val instantEnd = Instant.ofEpochMilli(endedAtMillis)
        val endedAt = DateTimeFormatter.ISO_INSTANT.format(instantEnd)

        val outputs = parseLenientJson(outputsString) ?: JsonNull

        val payload = buildJsonObject {
            put("end", buildJsonObject {
                put("project_id", projectId)
                put("id", spanId)
                put("ended_at", endedAt.toString())
                put("summary", buildJsonObject {
                    put("status", "OK")
                })
                put("output", outputs)
            })
        }

        return payload
    }

    companion object {
        internal suspend fun publishRootStartCall(
            span: ReadableSpan
        ) {
            val spanId = span.spanContext.spanId
            val traceId = span.spanContext.traceId

            val sourceName =
                span.getAttribute(AttributeKey.stringKey(FluentSpanAttributes.SPAN_SOURCE_NAME.key)) ?: ""
            val spanType =
                span.getAttribute(AttributeKey.stringKey(FluentSpanAttributes.SPAN_TYPE.key)) ?: "UNKNOWN"
            val spanInputs =
                span.getAttribute(AttributeKey.stringKey(FluentSpanAttributes.SPAN_INPUTS.key)) ?: ""
            val functionName =
                span.getAttribute(AttributeKey.stringKey(FluentSpanAttributes.SPAN_FUNCTION_NAME.key)) ?: span.name

            val startedAtMillis = Instant.now().toEpochMilli()

            val startPayload =
                buildStartCall(
                    TracingSessionProvider.currentExperimentId,
                    spanId,
                    traceId,
                    sourceName,
                    spanType,
                    spanInputs,
                    functionName,
                    span.name,
                    startedAtMillis,
                    JsonNull
                )

            KotlinWandbClient.client.post("$WANDB_API/start") {
                contentType(ContentType.Application.Json)
                headers {
                    append("Authorization", WANDB_USER_API_KEY)
                }
                setBody(startPayload)
            }
        }

        private fun parseLenientJson(raw: String?): JsonElement? {
            return try {
                raw?.let { Json.parseToJsonElement(it) }
            } catch (_: Exception) {
                raw?.let { JsonPrimitive(it) }
            }
        }

        private fun buildStartCall(
            currentExperimentId: String,
            spanId: String,
            traceId: String,
            sourceName: String,
            spanType: String,
            spanInputs: String,
            functionName: String,
            displayName: String,
            startedAtMillis: Long,
            parentSpanId: JsonElement,
        ): JsonObject {
            val projectId = "$USER_ID/$currentExperimentId"

            val inputs = parseLenientJson(spanInputs)

            val inputMessages = when (val messages = inputs?.jsonObject?.get("messages")) {
                is JsonArray -> messages
                else -> inputs
            }

            val temperature = inputs?.jsonObject?.get("temperature")?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
            val model = inputs?.jsonObject?.get("model")?.jsonPrimitive?.contentOrNull

            val opName = "weave:///$projectId/op/$functionName:$spanId"

            val instantStart = Instant.ofEpochMilli(startedAtMillis)
            val startedAt = DateTimeFormatter.ISO_INSTANT.format(instantStart)

            val payload = buildJsonObject {
                put("start", buildJsonObject {
                    put("project_id", projectId)
                    put("id", spanId)
                    put("trace_id", traceId)
                    put("parent_id", parentSpanId)
                    put("started_at", startedAt)
                    put("op_name", opName)
                    put("display_name", displayName)
                    put("attributes", buildJsonObject {
                        put("spanType", spanType)
                        put("sourceName", sourceName)
                    })
                    put("inputs", buildJsonObject {
                        when (inputMessages) {
                            is JsonObject -> {
                                inputMessages.forEach { (key, value) ->
                                    put(key, value)
                                }
                            }

                            is JsonArray -> {
                                put("messages", inputMessages)
                            }

                            else -> {}
                        }
                        model?.let { put("model", JsonPrimitive(it)) }
                        temperature?.let { put("temperature", JsonPrimitive(it)) }
                    })
                })
            }

            return payload
        }
    }

    override suspend fun publishTrace(trace: List<SpanData>) {
        val requestUrl = "$WANDB_API/upsert_batch"

        val parentSpan: SpanData = trace.find { it.parentSpanId == SpanId.getInvalid() }
            ?: throw IllegalStateException("Parent span not found.")

        val startedAtMillis = parentSpan.startEpochNanos / 1_000_000
        val endedAtMillis = parentSpan.endEpochNanos / 1_000_000

        trace.forEach { span ->
            val traceId = span.traceId
            val spanId = span.spanId
            val spanType = span.getAttribute(FluentSpanAttributes.SPAN_TYPE) ?: "UNKNOWN"
            val sourceName = span.getAttribute(FluentSpanAttributes.SPAN_SOURCE_NAME) ?: ""

            val parentSpanId = if (span.parentSpanId.toString() != SpanId.getInvalid()) {
                JsonPrimitive(span.parentSpanId)
            } else {
                JsonNull
            }

            val inputs = span.getAttribute(FluentSpanAttributes.SPAN_INPUTS) ?: ""

            val functionName = span.getAttribute(FluentSpanAttributes.SPAN_FUNCTION_NAME) ?: span.name

            val startPayload =
                buildStartCall(
                    TracingSessionProvider.currentExperimentId,
                    spanId,
                    traceId,
                    sourceName,
                    spanType,
                    inputs,
                    functionName,
                    span.name,
                    startedAtMillis,
                    parentSpanId
                )

            val outputs = span.getAttribute(FluentSpanAttributes.SPAN_OUTPUTS) ?: ""

            val endPayload = buildEndCall(TracingSessionProvider.currentExperimentId, spanId, endedAtMillis, outputs)

            val json = buildJsonObject {
                put("batch", buildJsonArray {
                    add(buildJsonObject {
                        put("req", startPayload)
                    })

                    add(buildJsonObject {
                        put("req", endPayload)
                    })
                })
            }

            val jsonString = Json.encodeToString(JsonObject.serializer(), json)

            KotlinWandbClient.client.post(requestUrl) {
                contentType(ContentType.Application.Json)
                headers {
                    append("Content-Type", "application/json")
                    append("Authorization", WANDB_USER_API_KEY)
                }
                setBody(jsonString)
            }
        }
    }
}
