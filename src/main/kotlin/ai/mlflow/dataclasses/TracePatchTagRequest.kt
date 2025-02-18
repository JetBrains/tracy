package org.example.ai.mlflow.dataclasses

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.trace.data.SpanData
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import org.example.ai.mlflow.Tag

fun List<SpanData>.toTracePatchTagRequest() =
    Tag(
        key = "mlflow.traceSpans",
        value = Json.encodeToString(this.map { spanData ->
            TracePatchTagRequest(
                // TODO change to function name, not a name of the span
                name = spanData.name,
                type = "UNKNOWN",
                inputs = spanData.attributes[AttributeKey.stringKey("mlflow.spanInputs")]
            )
        })
    )

@Serializable
data class TracePatchTagRequest(
    @SerialName("name") val name: String,
    @SerialName("type") val type: String,
    @SerialName("inputs") val inputs: String?
) {
    override fun toString(): String {
        return Json.encodeToString(this)
    }
}