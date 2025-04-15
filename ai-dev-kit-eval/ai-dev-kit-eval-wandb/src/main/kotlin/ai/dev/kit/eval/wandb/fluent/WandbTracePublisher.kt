package ai.dev.kit.eval.wandb.fluent

import ai.dev.kit.core.fluent.processor.TracePublisher
import ai.dev.kit.eval.base.fluent.FluentSpanAttributes
import ai.dev.kit.eval.base.fluent.getAttribute
import ai.dev.kit.eval.wandb.KotlinWandbClient
import ai.dev.kit.eval.wandb.KotlinWandbClient.USER_ID
import ai.dev.kit.eval.wandb.KotlinWandbClient.WANDB_API
import ai.dev.kit.eval.wandb.KotlinWandbClient.WANDB_USER_API_KEY
import ai.dev.kit.eval.wandb.KotlinWandbClient.currentExperimentId
import io.ktor.client.request.*
import io.ktor.http.*
import io.opentelemetry.api.trace.SpanId
import io.opentelemetry.sdk.trace.data.SpanData
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.format.DateTimeFormatter

object WandBTracePublisher : TracePublisher {
    override suspend fun publishTrace(trace: List<SpanData>) {
        val projectId = "$USER_ID/$currentExperimentId"
        val requestUrl = "$WANDB_API/upsert_batch"

        val parentSpan: SpanData = trace.find { it.parentSpanId == SpanId.getInvalid() }
            ?: throw IllegalStateException("Parent span not found.")

        val startedAtMillis = parentSpan.startEpochNanos / 1_000_000
        val endedAtMillis = parentSpan.endEpochNanos / 1_000_000

        val instantStart = Instant.ofEpochMilli(startedAtMillis)
        val instantEnd = Instant.ofEpochMilli(endedAtMillis)

        val startedAt = DateTimeFormatter.ISO_INSTANT.format(instantStart)
        val endedAt = DateTimeFormatter.ISO_INSTANT.format(instantEnd)


        trace.forEach { span ->
            val traceId = span.traceId
            val spanId = span.spanId
            val spanType = span.getAttribute(FluentSpanAttributes.SPAN_TYPE)
            val sourceName = span.getAttribute(FluentSpanAttributes.SPAN_SOURCE_NAME)

            val parentSpanId = if (span.parentSpanId.toString() != SpanId.getInvalid()) {
                JsonPrimitive(span.parentSpanId)
            } else {
                JsonNull
            }

            val inputs = parseLenientJson(span.getAttribute(FluentSpanAttributes.SPAN_INPUTS))
            val outputs = parseLenientJson(span.getAttribute(FluentSpanAttributes.SPAN_OUTPUTS))

            val inputMessages: JsonArray = when (val inputMessagesElement = inputs?.jsonObject?.get("messages")) {
                is JsonArray -> inputMessagesElement
                else -> buildJsonArray {
                    add(buildJsonObject {
                        put("content", inputs.toString())
                    })
                }
            }

            val temperature = inputs?.jsonObject?.get("temperature")?.jsonPrimitive?.content?.toDouble()
            val model = inputs?.jsonObject?.get("model")?.jsonPrimitive?.content

            val functionName = span.getAttribute(FluentSpanAttributes.SPAN_FUNCTION_NAME) ?: span.name
            val opIdHash = span.spanContext.spanId

            val opName = "weave:///$projectId/op/$functionName:$opIdHash"

            val json = buildJsonObject {
                put("batch", buildJsonArray {
                    // START
                    add(buildJsonObject {
                        put("mode", "start")
                        put("req", buildJsonObject {
                            put("start", buildJsonObject {
                                put("project_id", projectId)
                                put("id", spanId)
                                put("trace_id", traceId)
                                put("parent_id", parentSpanId)
                                put("op_name", opName)
                                put("display_name", span.name)
                                put("started_at", startedAt.toString())
                                put("attributes", buildJsonObject {
                                    put("spanType", spanType)
                                    put("sourceName", sourceName)
                                })
                                put("inputs", buildJsonObject {
                                    put("messages", inputMessages)
                                    put("model", model)
                                    put("temperature", temperature)
                                })
                            })
                        })
                    })

                    // END
                    add(buildJsonObject {
                        put("mode", "end")
                        put("req", buildJsonObject {
                            put("end", buildJsonObject {
                                put("project_id", projectId)
                                put("id", spanId)
                                put("ended_at", endedAt.toString())
                                put("summary", buildJsonObject {
                                    put("status", "OK")
                                })
                                put("output", outputs ?: JsonNull)
                            })
                        })
                    })
                })
            }

            val jsonString = Json.encodeToString(JsonObject.serializer(), json)

            val client = KotlinWandbClient.client

            client.post(requestUrl) {
                contentType(ContentType.Application.Json)
                headers {
                    append("Content-Type", "application/json")
                    append("Authorization", WANDB_USER_API_KEY)
                }
                setBody(jsonString)
            }
        }
    }

    private fun parseLenientJson(raw: String?): JsonElement? {
        return try {
            raw?.let { Json.parseToJsonElement(it) }
        } catch (_: Exception) {
            raw?.let { JsonPrimitive(it) }
        }
    }
}
