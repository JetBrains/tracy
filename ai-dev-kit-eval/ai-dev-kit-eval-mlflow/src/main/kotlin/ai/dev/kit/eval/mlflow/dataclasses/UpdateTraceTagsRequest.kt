package ai.dev.kit.eval.mlflow.dataclasses

import ai.dev.kit.eval.base.dataclasses.Tag
import ai.dev.kit.eval.base.fluent.FluentSpanAttributes
import io.opentelemetry.sdk.trace.data.SpanData
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import ai.dev.kit.eval.base.fluent.getAttribute

fun List<SpanData>.toUpdateTraceTagsRequest() =
    Tag(
        key = "traceSpans",
        value = Json.encodeToString(this.map { spanData ->
            UpdateTraceTagsRequest(
                name = spanData.name,
                type = spanData.getAttribute(FluentSpanAttributes.SPAN_TYPE),
                inputs = spanData.getAttribute(FluentSpanAttributes.SPAN_INPUTS)
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
