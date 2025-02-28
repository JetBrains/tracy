package org.example.ai.mlflow.dataclasses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.example.ai.mlflow.RequestMetadata
import org.example.ai.mlflow.Tag
import org.example.ai.mlflow.getCurrentTimestamp
import java.time.Instant

internal fun createTracePostRequest(
    experimentId: String,
    startTime: Long = Instant.now().toEpochMilli(),
    traceCreationPath: String,
    traceName: String,
    runId: String? = null
)  = TracePostRequest(
        experimentId = experimentId,
        timestampMs = startTime,
        requestMetadata = listOf(
            RequestMetadata(key = "mlflow.trace_schema.version", value = "2")
        ),
        tags = buildList {
            add(Tag("mlflow.source.name", traceCreationPath))
            add(Tag("mlflow.source.type", "LOCAL"))
            add(Tag("mlflow.traceName", traceName))
            runId?.let {
                add(Tag("mlflow.sourceRun", runId))
            }
        }
    )

@Serializable
data class TracePostRequest(
    @SerialName("experiment_id") val experimentId: String,
    @SerialName("timestamp_ms") val timestampMs: Long = getCurrentTimestamp(),
    @SerialName("request_metadata") val requestMetadata: List<RequestMetadata>,
    @SerialName("tags") val tags: List<Tag>
)
