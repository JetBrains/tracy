package ai.dev.kit.providers.mlflow.dataclasses

import ai.dev.kit.core.fluent.dataclasses.RequestMetadata
import ai.dev.kit.core.fluent.dataclasses.Tag
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ai.dev.kit.providers.mlflow.getCurrentTimestamp
import java.time.Instant

internal fun createTracePostRequest(
    experimentId: String,
    runId: String?,
    startTime: Long = Instant.now().toEpochMilli(),
    traceCreationPath: String,
    traceName: String
)  = TracePostRequest(
        experimentId = experimentId,
        timestampMs = startTime,
        requestMetadata = listOfNotNull(
            RequestMetadata(key = "mlflow.trace_schema.version", value = "2"),
            runId?.let { RequestMetadata(key = "mlflow.sourceRun", value = it) }
        ),
        tags = listOf(
            Tag("mlflow.source.name", traceCreationPath),
            Tag("mlflow.source.type", "LOCAL"),
            Tag("mlflow.traceName", traceName),
        )
    )

@Serializable
data class TracePostRequest(
    @SerialName("experiment_id") val experimentId: String,
    @SerialName("timestamp_ms") val timestampMs: Long = getCurrentTimestamp(),
    @SerialName("request_metadata") val requestMetadata: List<RequestMetadata>,
    @SerialName("tags") val tags: List<Tag>
)
