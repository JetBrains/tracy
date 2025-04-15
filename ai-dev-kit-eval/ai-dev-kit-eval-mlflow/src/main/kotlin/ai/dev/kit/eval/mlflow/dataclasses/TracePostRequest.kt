package ai.dev.kit.eval.mlflow.dataclasses

import ai.dev.kit.eval.base.dataclasses.RequestMetadata
import ai.dev.kit.eval.base.dataclasses.Tag
import ai.dev.kit.eval.mlflow.getCurrentTimestamp
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

internal fun createTracePostRequest(
    experimentId: String,
    runId: String?,
    startTime: Long = Instant.now().toEpochMilli(),
    traceCreationPath: String,
    traceName: String
) = TracePostRequest(
    experimentId = experimentId,
    timestampMs = startTime,
    requestMetadata = listOfNotNull(
        RequestMetadata(key = "trace_schema.version", value = "2"),
        runId?.let { RequestMetadata(key = "sourceRun", value = it) }
    ),
    tags = listOf(
        Tag("source.name", traceCreationPath),
        Tag("source.type", "LOCAL"),
        Tag("traceName", traceName),
    )
)

@Serializable
data class TracePostRequest(
    @SerialName("experiment_id") val experimentId: String,
    @SerialName("timestamp_ms") val timestampMs: Long = getCurrentTimestamp(),
    @SerialName("request_metadata") val requestMetadata: List<RequestMetadata>,
    @SerialName("tags") val tags: List<Tag>
)
