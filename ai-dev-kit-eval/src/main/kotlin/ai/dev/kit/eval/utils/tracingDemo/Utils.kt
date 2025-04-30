package ai.dev.kit.eval.utils.tracingDemo

import ai.dev.kit.core.fluent.dataclasses.RequestMetadata
import ai.dev.kit.core.fluent.dataclasses.RunStatus
import ai.dev.kit.core.fluent.dataclasses.Tag
import ai.dev.kit.eval.utils.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

fun getCurrentTimestamp(): Long {
    return Instant.now().toEpochMilli()
}

fun createTracePostRequest(
    experimentId: String,
    runId: String?,
    startTime: Long = Instant.now().toEpochMilli(),
    traceCreationPath: String,
    traceName: String
) = TracePostRequest(
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

data class RunTag(
    val color: String
)

data class TestResult<
        AIInputT : AIInput,
        GroundTruthT : GroundTruth,
        AIOutputT : AIOutput,
        EvalResultT : EvalResult
        >(
    val testCase: TestCase<AIInputT, GroundTruthT>,
    val output: AIOutputT,
    val evalResult: EvalResultT
)


data class RunResults<
        AIInputT : AIInput,
        GroundTruthT : GroundTruth,
        AIOutputT : AIOutput,
        EvalResultT : EvalResult
        >(
    val testResults: MutableList<TestResult<AIInputT, GroundTruthT, AIOutputT, EvalResultT>>,
    val runId: String,
    var finalStatus: RunStatus,
)