package ai.dev.kit.providers.mlflow

import ai.dev.kit.tracing.fluent.dataclasses.*
import ai.dev.kit.providers.mlflow.KotlinMlflowClient.USER_ID
import ai.dev.kit.providers.mlflow.dataclasses.SpanArtifactsRequest
import ai.dev.kit.providers.mlflow.dataclasses.TracePatchRequest
import ai.dev.kit.eval.utils.TracePostRequest
import ai.dev.kit.eval.utils.getCurrentTimestamp
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.mlflow.api.proto.Service
import org.mlflow.tracking.MlflowClient
import kotlin.jvm.optionals.getOrNull

private fun getCallerInfo(): String? {
    val stackTrace = Throwable().stackTrace
    return stackTrace.getOrNull(1)?.let {
        "${it.className}.${it.methodName}"
    }
}

suspend fun createRun(name: String, experimentId: String, source: String? = getCallerInfo()): Run {
    val tags = listOfNotNull(
        Tag(key = "mlflow.user", value = USER_ID),
        source?.let { Tag(key = "mlflow.source.name", value = it) },
        Tag(key = "mlflow.source.type", value = "LOCAL")
    )
    val run = RunCreationData(
        experimentId = experimentId,
        userId = USER_ID,
        runName = name,
        startTime = getCurrentTimestamp(),
        tags = tags
    )

    val result = KotlinMlflowClient.client.post("${KotlinMlflowClient.ML_FLOW_API}/runs/create") {
        contentType(ContentType.Application.Json)
        setBody(run)
    }

    val runResult = Json.decodeFromString<RunResponse>(result.bodyAsText())
    return runResult.run
}


fun createRun(
    client: MlflowClient,
    name: String,
    experimentId: String,
    source: String? = getCallerInfo()
): Service.RunInfo? {
    val runData = Service.CreateRun.newBuilder().apply {
        setExperimentId(experimentId)
        setUserId(USER_ID)
        setRunName(name)
        setStartTime(getCurrentTimestamp())

        listOfNotNull(
            Tag(key = "mlflow.user", value = USER_ID),
            source?.let { Tag(key = "mlflow.source.name", value = it) },
            Tag(key = "mlflow.source.type", value = "LOCAL")
        ).forEach { tag ->
            addTags(Service.RunTag.newBuilder().setKey(tag.key).setValue(tag.value).build())
        }
    }.build()

    val runInfo = client.createRun(runData)
    return runInfo
}

suspend fun updateRun(runId: String, runStatus: RunStatus) {
    KotlinMlflowClient.client.post("${KotlinMlflowClient.ML_FLOW_API}/runs/update") {
        contentType(ContentType.Application.Json)
        setBody(
            mapOf(
                "run_id" to runId, "status" to runStatus.name, "end_time" to getCurrentTimestamp().toString()
            )
        )
    }
}

suspend fun logModel(runId: String, modelJson: String, mlFlowUrl: String = KotlinMlflowClient.ML_FLOW_API) {
    KotlinMlflowClient.client.post("${mlFlowUrl}/runs/log-model") {
        contentType(ContentType.Application.Json)
        setBody(
            mapOf(
                "run_id" to runId, "model_json" to modelJson
            )
        )
    }
}

suspend fun uploadArtifact(path: String, content: String) {
    KotlinMlflowClient.client.put("${KotlinMlflowClient.ML_FLOW_ARTIFACTS_API}/artifacts/$path") {
        contentType(ContentType.Application.Json)
        setBody(content)
    }
}

fun logMlflowMetric(client: MlflowClient, runId: String, key: String, value: Double) {
    client.logMetric(runId, key, value)
}

suspend fun createExperiment(name: String): String {
    val response: HttpResponse =
        KotlinMlflowClient.client.post("${KotlinMlflowClient.ML_FLOW_API}/experiments/create") {
            contentType(ContentType.Application.Json)
            setBody(
                mapOf(
                    "name" to name,
                    "artifact_location" to "file:///Users/Anton.Bragin/PycharmProjects/mlflow-test/mlruns/0"
                )
            )
        }

    return Json.parseToJsonElement(response.bodyAsText()).jsonObject["experiment_id"]?.jsonPrimitive?.content!!
}

fun createExperiment(client: MlflowClient, name: String): String? {
    val experimentData = Service.CreateExperiment.newBuilder().apply {
        setName(name)
    }.build()

    // todo: throws when experiment already exists
    val experimentId = client.createExperiment(experimentData)

    return experimentId
}

suspend fun getExperiment(experimentId: String): Experiment {
    val response: HttpResponse = KotlinMlflowClient.client.get("${KotlinMlflowClient.ML_FLOW_API}/experiments/get") {
        contentType(ContentType.Application.Json)
        setBody(mapOf("experiment_id" to experimentId))
    }

    val experimentResponse = Json.decodeFromString<ExperimentResponse>(response.bodyAsText())
    return experimentResponse.experiment
}

suspend fun getTraces(experimentId: String): TracesResponse {
    val response: HttpResponse = KotlinMlflowClient.client.get("${KotlinMlflowClient.ML_FLOW_API}/traces") {
        parameter("experiment_ids", experimentId)
        contentType(ContentType.Application.Json)
    }

    return Json.decodeFromString<TracesResponse>(response.bodyAsText())
}

suspend fun logBatch(runId: String, metrics: List<Metric>, params: List<Param> = emptyList()) {
    val runData = RunMetricsData(
        runId = runId, metrics = metrics, params = params
    )

    KotlinMlflowClient.client.post("${KotlinMlflowClient.ML_FLOW_API}/runs/log-batch") {
        contentType(ContentType.Application.Json)
        setBody(runData)
    }
}

suspend fun createTrace(tracePostRequest: TracePostRequest): TraceInfo {
    val postResponse = KotlinMlflowClient.client.post("${KotlinMlflowClient.ML_FLOW_API}/traces") {
        contentType(ContentType.Application.Json)
        setBody(tracePostRequest)
    }
    return Json.decodeFromString<TraceInfoResponse>(postResponse.bodyAsText()).traceInfo
}

internal suspend fun updateTraceTags(requestId: String, updateTagRequest: Tag) {
    KotlinMlflowClient.client.patch("${KotlinMlflowClient.ML_FLOW_API}/traces/$requestId/tags") {
        contentType(ContentType.Application.Json)
        setBody(updateTagRequest)
    }
}

internal suspend fun uploadTraceArtifacts(
    experimentId: String, requestId: String, spanArtifactsRequest: SpanArtifactsRequest
) {
    KotlinMlflowClient.client.put("${KotlinMlflowClient.ML_FLOW_ARTIFACTS_API}/artifacts/$experimentId/traces/$requestId/artifacts/traces.json") {
        contentType(ContentType.Application.Json)
        setBody(spanArtifactsRequest)
    }
}

internal suspend fun patchTrace(
    tracePatchRequest: TracePatchRequest,
) {
    KotlinMlflowClient.client.patch("${KotlinMlflowClient.ML_FLOW_API}/traces/${tracePatchRequest.requestId}") {
        contentType(ContentType.Application.Json)
        setBody(tracePatchRequest)
    }
}

suspend fun setTag(runId: String?, key: String, value: String) {
    KotlinMlflowClient.client.post("${KotlinMlflowClient.ML_FLOW_API}/runs/set-tag") {
        contentType(ContentType.Application.Json)
        setBody(
            mapOf(
                "run_id" to runId, "run_uuid" to runId, "key" to key, "value" to value
            )
        )
    }
}

fun getExperimentByName(client: MlflowClient, experimentName: String): Service.Experiment? {
    return client.getExperimentByName(experimentName).getOrNull()
}

fun getExperiment(client: MlflowClient, experimentId: String): Service.Experiment? {
    return client.getExperiment(experimentId)
}

@Serializable
data class RunMetricsData(
    @SerialName("run_id") val runId: String,
    val metrics: List<Metric>,
    val params: List<Param>,
    val tags: List<Tag> = emptyList()
)

@Serializable
data class Metric(
    val key: String, val value: Double, val timestamp: Long = getCurrentTimestamp()
)

@Serializable
data class Param(
    val key: String, val value: String
)

@Serializable
data class ExperimentResponse(
    @SerialName("experiment") val experiment: Experiment
)

@Serializable
data class Experiment(
    @SerialName("experiment_id") val experimentId: String,
    @SerialName("name") val name: String,
    @SerialName("artifact_location") val artifactLocation: String,
    @SerialName("lifecycle_stage") val lifecycleStage: String,
    @SerialName("last_update_time") val lastUpdateTime: Long,
    @SerialName("creation_time") val creationTime: Long
)


suspend fun getRun(runId: String): Run {
    val response: HttpResponse = KotlinMlflowClient.client.get("${KotlinMlflowClient.ML_FLOW_API}/runs/get") {
        contentType(ContentType.Application.Json)
        setBody(mapOf("run_id" to runId))
    }

    val runResult = Json.decodeFromString<RunResponse>(response.bodyAsText())
    return runResult.run
}

suspend fun getModel(runId: String) {
    val response: HttpResponse = KotlinMlflowClient.client.get("${KotlinMlflowClient.ML_FLOW_API}/runs/get") {
        contentType(ContentType.Application.Json)
        setBody(mapOf("run_id" to runId))
    }
}
