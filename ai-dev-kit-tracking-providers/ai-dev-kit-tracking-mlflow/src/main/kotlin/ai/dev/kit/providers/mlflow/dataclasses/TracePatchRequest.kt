package ai.dev.kit.providers.mlflow.dataclasses

import ai.dev.kit.core.fluent.dataclasses.RequestMetadata
import ai.dev.kit.core.fluent.dataclasses.Tag
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ai.dev.kit.providers.mlflow.getCurrentTimestamp

@Serializable
data class TracePatchRequest(
    @SerialName("request_id") val requestId: String,
    @SerialName("timestamp_ms") val timestampMs: Long = getCurrentTimestamp(),
    @SerialName("status") val status: String,
    @SerialName("request_metadata") val requestMetadata: List<RequestMetadata>,
    @SerialName("tags") val tags: List<Tag>
)
