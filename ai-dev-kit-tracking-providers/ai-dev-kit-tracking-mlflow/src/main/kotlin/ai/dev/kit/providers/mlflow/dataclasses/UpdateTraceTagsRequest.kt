package ai.dev.kit.providers.mlflow.dataclasses

import ai.dev.kit.providers.mlflow.fluent.MlflowFluentSpanAttributes
import io.opentelemetry.sdk.trace.data.SpanData
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import ai.dev.kit.providers.mlflow.Tag
import ai.dev.kit.providers.mlflow.getAttribute

fun List<SpanData>.toUpdateTraceTagsRequest() =
    Tag(
        key = "mlflow.traceSpans",
        value = Json.encodeToString(this.map { spanData ->
            UpdateTraceTagsRequest(
                name = spanData.name,
                type = spanData.getAttribute(MlflowFluentSpanAttributes.MLFLOW_SPAN_TYPE),
                inputs = spanData.getAttribute(MlflowFluentSpanAttributes.MLFLOW_SPAN_INPUTS)
            )
        })
    )

@Serializable
data class UpdateTraceTagsRequest(
    @SerialName("name") val name: String,
    @SerialName("type") val type: String?,
    @SerialName("inputs") val inputs: String?
) {
    override fun toString(): String {
        return Json.encodeToString(this)
    }
}
