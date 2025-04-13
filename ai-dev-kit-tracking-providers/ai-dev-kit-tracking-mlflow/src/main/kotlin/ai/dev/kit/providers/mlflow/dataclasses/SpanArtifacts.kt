package ai.dev.kit.providers.mlflow.dataclasses

import ai.dev.kit.providers.mlflow.fluent.MlflowFluentSpanAttributes
import io.opentelemetry.api.trace.SpanId
import io.opentelemetry.sdk.trace.data.SpanData
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ai.dev.kit.providers.mlflow.getAttribute

fun List<SpanData>.toSpanArtifactsRequest(requestId: String) = this.map { spanData ->
    Span(
        name = spanData.name,
        context = SpanContext(
            spanId = spanData.spanId,
            traceId = spanData.traceId
        ),
        parentId = spanData.parentSpanId.takeUnless { it == SpanId.getInvalid() },
        startTime = spanData.startEpochNanos,
        endTime = spanData.endEpochNanos,
        statusCode = "OK",
        attributes = Attributes(
            traceRequestId = requestId,
            spanType = spanData.getAttribute(MlflowFluentSpanAttributes.MLFLOW_SPAN_TYPE) ?: "UNKNOWN",
            spanFunctionName = spanData.getAttribute(MlflowFluentSpanAttributes.MLFLOW_SPAN_FUNCTION_NAME),
            spanInputs = spanData.getAttribute(MlflowFluentSpanAttributes.MLFLOW_SPAN_INPUTS),
            spanOutputs = spanData.getAttribute(MlflowFluentSpanAttributes.MLFLOW_SPAN_OUTPUTS)

        ),
        events = emptyList()
    )
}

@Serializable
data class SpanArtifactsRequest(
    @SerialName("spans") val spans: List<Span>,
    @SerialName("request") val request: String?,
    @SerialName("response") val response: String?,
)

@Serializable
data class Span(
    @SerialName("name") val name: String,
    @SerialName("context") val context: SpanContext,
    @SerialName("parent_id") val parentId: String? = null,
    @SerialName("start_time") val startTime: Long,
    @SerialName("end_time") val endTime: Long,
    @SerialName("status_code") val statusCode: String,
    @SerialName("status_message") val statusMessage: String = "",
    @SerialName("attributes") val attributes: Attributes,
    @SerialName("events") val events: List<String> = emptyList()
)

@Serializable
data class SpanContext(
    @SerialName("span_id") val spanId: String, @SerialName("trace_id") val traceId: String
)

@Serializable
data class Attributes(
    @SerialName("mlflow.traceRequestId") val traceRequestId: String,
    @SerialName("mlflow.spanType") val spanType: String,
    @SerialName("mlflow.spanFunctionName") val spanFunctionName: String?,
    @SerialName("mlflow.spanInputs") val spanInputs: String?,
    @SerialName("mlflow.spanOutputs") val spanOutputs: String?
)
