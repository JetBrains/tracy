package org.example.ai.mlflow.dataclasses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class TraceInfoResponse(
    @SerialName("trace_info") val traceInfo: TraceInfo
)

@Serializable
data class TraceInfo(
    @SerialName("request_id") val requestId: String,
    @SerialName("experiment_id") val experimentId: String,
    @SerialName("timestamp_ms") val timestampMs: Long,
    @SerialName("execution_time_ms") val executionTimeMs: Int,
    @SerialName("status") val status: String,
    @SerialName("request_metadata") val requestMetadata: List<RequestMetadata>? = null,
    @SerialName("tags") val tags: List<Tag>
)

@Serializable
data class RequestMetadata(
    @SerialName("key") val key: String,
    @SerialName("value") val value: String
)

@Serializable
data class Tag(
    @SerialName("key") val key: String,
    @SerialName("value") val value: String
)