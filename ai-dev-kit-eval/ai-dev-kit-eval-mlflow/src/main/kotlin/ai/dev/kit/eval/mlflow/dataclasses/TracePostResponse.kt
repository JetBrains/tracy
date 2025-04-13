package ai.dev.kit.eval.mlflow.dataclasses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ai.dev.kit.eval.mlflow.RequestMetadata
import ai.dev.kit.eval.mlflow.Tag

@Serializable
data class TracesResponse(
    @SerialName("traces") val traces: List<TraceInfo>
)

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
    @SerialName("request_metadata") val requestMetadata: List<RequestMetadata>,
    @SerialName("tags") val tags: List<Tag>
)
