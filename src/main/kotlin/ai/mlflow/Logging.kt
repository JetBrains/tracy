package org.example.ai.mlflow

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.example.ai.mlflow.dataclasses.RequestMetadata
import org.example.ai.mlflow.dataclasses.TraceInfoResponse
import org.example.ai.mlflow.dataclasses.TracePatchRequest
import org.example.ai.mlflow.dataclasses.TracePostRequest
import org.example.ai.model.ModelData
import org.example.ai.model.createModelYaml
import org.mlflow.api.proto.Service
import org.mlflow.tracking.MlflowClient
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Instant

const val ML_FLOW_API = "http://localhost:5001/api/2.0/mlflow"
private const val USER_ID = "Anton.Bragin"

private val client = HttpClient(CIO) {
    install(ContentNegotiation) {
        json()
    }
}

@Serializable
data class RunCreationData(
    @SerialName("experiment_id") val experimentId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("run_name") val runName: String,
    @SerialName("start_time") val startTime: Long,
    @SerialName("tags") val tags: List<Tag> = emptyList()
)

@Serializable
data class Tag(
    @SerialName("key") val key: String,
    @SerialName("value") val value: String
)

@Serializable
data class RunResponse(
    @SerialName("run") val run: Run
)


@Serializable
data class Run(
    @SerialName("info") val info: RunInfo,
    @SerialName("data") val data: RunData,
    @SerialName("inputs") val inputs: Inputs
)

@Serializable
data class RunInfo(
    @SerialName("run_uuid") val runUuid: String,
    @SerialName("experiment_id") val experimentId: String,
    @SerialName("run_name") val runName: String,
    @SerialName("user_id") val userId: String,
    @SerialName("status") val status: String,
    @SerialName("start_time") val startTime: Long,
    @SerialName("artifact_uri") val artifactUri: String,
    @SerialName("lifecycle_stage") val lifecycleStage: String,
    @SerialName("run_id") val runId: String
)

@Serializable
data class RunData(
    @SerialName("tags") val tags: List<Tag>
)

@Serializable
data class Inputs(
    val pass: String? = null
)

enum class RunStatus {
    RUNNING,
    SCHEDULED,
    FINISHED,
    FAILED,
    KILLED
}

fun getCurrentTimestamp(): Long {
    return Instant.now().toEpochMilli()
}

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

    val result = client.post("${ML_FLOW_API}/runs/create") {
        contentType(ContentType.Application.Json)
        setBody(run)
    }

    val runResult = Json.decodeFromString<RunResponse>(result.bodyAsText())
    return runResult.run
}

fun createRun(client: MlflowClient, name: String, experimentId: String, source: String? = getCallerInfo()): Service.RunInfo? {
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
    client.post("${ML_FLOW_API}/runs/update") {
        contentType(ContentType.Application.Json)
        setBody(
            mapOf(
                "run_id" to runId,
                "status" to runStatus.name,
                "end_time" to getCurrentTimestamp().toString()
            )
        )
    }
}

suspend fun logModel(runId: String, modelJson: String) {
    client.post("${ML_FLOW_API}/runs/log-model") {
        contentType(ContentType.Application.Json)
        setBody(
            mapOf(
                "run_id" to runId,
                "model_json" to modelJson
            )
        )
    }
}

fun logModelData(artifactUri: String, modelData: ModelData) {
    val artifactPath = Paths.get(URI(artifactUri))
    val modelPath = artifactPath.resolve("model")
    val modelFilePath = modelPath.resolve("MLmodel")

    Files.createDirectories(modelPath)
    Files.createFile(modelFilePath)

    val yamlString = createModelYaml(modelData)
    Files.write(modelFilePath, yamlString.toByteArray(StandardCharsets.UTF_8))
}

suspend fun createExperiment(name: String): String {
    val response: HttpResponse = client.post("${ML_FLOW_API}/experiments/create") {
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

suspend fun getExperiment(experimentId: String): Experiment {
    val response: HttpResponse = client.get("${ML_FLOW_API}/experiments/get") {
        contentType(ContentType.Application.Json)
        setBody(mapOf("experiment_id" to experimentId))
    }

    val experimentResponse = Json.decodeFromString<ExperimentResponse>(response.bodyAsText())
    return experimentResponse.experiment
}

suspend fun logBatch(runId: String, metrics: List<Metric>, params: List<Param> = emptyList()) {
    val runData = RunMetricsData(
        runId = runId,
        metrics = metrics,
        params = params
    )

    client.post("${ML_FLOW_API}/runs/log-batch") {
        contentType(ContentType.Application.Json)
        setBody(runData)
    }
}

suspend fun trace(experimentId: String, runId: String, source: String? = null, tracingJson: JsonObject) {
    val trace = TracePostRequest(
        experimentId = experimentId,
        requestMetadata = emptyList(),
        tags = listOfNotNull(
            source?.let { org.example.ai.mlflow.dataclasses.Tag("mlflow.source.name", it) },
            source?.let { org.example.ai.mlflow.dataclasses.Tag("mlflow.source.type", "LOCAL") }
        )
    )

    val postResponse = client.post("${ML_FLOW_API}/traces") {
        contentType(ContentType.Application.Json)
        setBody(trace)
    }

    val traceResponse = Json.decodeFromString<TraceInfoResponse>(postResponse.bodyAsText()).traceInfo

    val patch = TracePatchRequest(
        traceResponse.requestId,
        status = "OK",
        requestMetadata = listOf(
            RequestMetadata(
                "mlflow.sourceRun", runId
            ),
            RequestMetadata(
                "mlflow.traceInputs", Json.encodeToString(tracingJson.jsonObject["request"])
            ),
            RequestMetadata(
                "mlflow.traceOutputs", Json.encodeToString(tracingJson.jsonObject["response"])
            ),
        ),
        tags = emptyList()
    )

    val patchResponse = client.patch("${ML_FLOW_API}/traces/${traceResponse.requestId}") {
        contentType(ContentType.Application.Json)
        setBody(patch)
    }

    // PATCH http://127.0.0.1:5000/api/2.0/mlflow/traces/3c30419648254c2baec65937d238957a/tags HTTP/1.1
    val samplePatchTagRequest = """
        {
            "key": "mlflow.traceSpans",
            "value": "[{\"name\": \"Completions\", \"type\": \"CHAT_MODEL\", \"inputs\": [\"messages\", \"model\"], \"outputs\": [\"id\", \"choices\", \"created\", \"model\", \"object\", \"service_tier\", \"system_fingerprint\", \"usage\"]}]"
        }
    """.trimIndent()

    // TODO: client.patch("${ML_FLOW_API}/traces/$traceId"
}

suspend fun setTag(runId: String, key: String, value: String) {
    client.post("${ML_FLOW_API}/runs/set-tag") {
        contentType(ContentType.Application.Json)
        setBody(
            mapOf(
                "run_id" to runId,
                "run_uuid" to runId,
                "key" to key,
                "value" to value
            )
        )
    }
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
    val key: String,
    val value: Double,
    val timestamp: Long = getCurrentTimestamp()
)

@Serializable
data class Param(
    val key: String,
    val value: String
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
    val response: HttpResponse = client.get("${ML_FLOW_API}/runs/get") {
        contentType(ContentType.Application.Json)
        setBody(mapOf("run_id" to runId))
    }

    val runResult = Json.decodeFromString<RunResponse>(response.bodyAsText())
    return runResult.run
}

suspend fun getModel(runId: String) {
    val response: HttpResponse = client.get("${ML_FLOW_API}/runs/get") {
        contentType(ContentType.Application.Json)
        setBody(mapOf("run_id" to runId))
    }

    println(response.bodyAsText())
}