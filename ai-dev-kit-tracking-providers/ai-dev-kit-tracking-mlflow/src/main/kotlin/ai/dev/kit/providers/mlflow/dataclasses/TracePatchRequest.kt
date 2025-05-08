package ai.dev.kit.providers.mlflow.dataclasses

import ai.dev.kit.eval.utils.getCurrentTimestamp
import ai.dev.kit.tracing.fluent.dataclasses.RequestMetadata
import ai.dev.kit.tracing.fluent.dataclasses.Tag
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TracePatchRequest(
    @SerialName("request_id") val requestId: String,
    @SerialName("timestamp_ms") val timestampMs: Long = getCurrentTimestamp(),
    @SerialName("status") val status: String,
    @SerialName("request_metadata") val requestMetadata: List<RequestMetadata>,
    @SerialName("tags") val tags: List<Tag>
)
