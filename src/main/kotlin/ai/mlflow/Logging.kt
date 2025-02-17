package org.example.ai.mlflow

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.example.ai.mlflow.dataclasses.Attributes
import org.example.ai.mlflow.dataclasses.RequestMetadata
import org.example.ai.mlflow.dataclasses.Span
import org.example.ai.mlflow.dataclasses.SpanArtifactsRequest
import org.example.ai.mlflow.dataclasses.SpanContext
import org.example.ai.mlflow.dataclasses.TraceInfoResponse
import org.example.ai.mlflow.dataclasses.TracePatchRequest
import org.example.ai.mlflow.dataclasses.TracePatchTagRequest
import org.example.ai.mlflow.dataclasses.TracePostRequest
import org.example.ai.mlflow.fluent.TraceInfo
import org.example.ai.mlflow.fluent.formatArgumentsAsJson
import org.example.ai.model.ModelData
import org.example.ai.model.createModelYaml
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Instant

private const val ML_FLOW_API = "http://localhost:8080/api/2.0/mlflow"
private const val ML_FLOW_ARTIFACTS_API = "http://localhost:8080/api/2.0/mlflow-artifacts"
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
    @SerialName("key") val key: String, @SerialName("value") val value: String
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
    RUNNING, SCHEDULED, FINISHED, FAILED, KILLED
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
        experimentId = experimentId, userId = USER_ID, runName = name, startTime = getCurrentTimestamp(), tags = tags
    )

    val result = client.post("${ML_FLOW_API}/runs/create") {
        contentType(ContentType.Application.Json)
        setBody(run)
    }

    val runResult = Json.decodeFromString<RunResponse>(result.bodyAsText())
    return runResult.run
}

suspend fun updateRun(runId: String, runStatus: RunStatus) {
    client.post("${ML_FLOW_API}/runs/update") {
        contentType(ContentType.Application.Json)
        setBody(
            mapOf(
                "run_id" to runId, "status" to runStatus.name, "end_time" to getCurrentTimestamp().toString()
            )
        )
    }
}

suspend fun logModel(runId: String, modelJson: String) {
    client.post("${ML_FLOW_API}/runs/log-model") {
        contentType(ContentType.Application.Json)
        setBody(
            mapOf(
                "run_id" to runId, "model_json" to modelJson
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
                "name" to name, "artifact_location" to "file:///Users/Anton.Bragin/PycharmProjects/mlflow-test/mlruns/0"
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
        runId = runId, metrics = metrics, params = params
    )

    client.post("${ML_FLOW_API}/runs/log-batch") {
        contentType(ContentType.Application.Json)
        setBody(runData)
    }
}

suspend fun trace(experimentId: String, runId: String? = null, traceInfo: TraceInfo) {
    val trace = TracePostRequest(
        experimentId = experimentId,
        requestMetadata = buildList {
            add(RequestMetadata(key = "mlflow.trace_schema.version", value = "2"))
        },
        tags = listOf(
            org.example.ai.mlflow.dataclasses.Tag("mlflow.source.name", traceInfo.path),
            org.example.ai.mlflow.dataclasses.Tag("mlflow.source.type", "LOCAL"),
            org.example.ai.mlflow.dataclasses.Tag("mlflow.traceName", traceInfo.methodName)
        )
    )

    val postResponse = client.post("${ML_FLOW_API}/traces") {
        contentType(ContentType.Application.Json)
        setBody(trace)
    }

    val traceResponse = Json.decodeFromString<TraceInfoResponse>(postResponse.bodyAsText()).traceInfo

    client.patch("${ML_FLOW_API}/traces/${traceResponse.requestId}/tags") {
        contentType(ContentType.Application.Json)
        setBody(
            Tag(
                key = "mlflow.traceSpans",
                value = listOf(
                    TracePatchTagRequest(
                        name = traceInfo.methodName,
                        inputs = traceInfo.arguments.map { it.name }
                    )
                ).toString()
            )
        )
    }

    // Add artifacts
    val spanArtifacts = SpanArtifactsRequest(
        spans = listOf(
            Span(
                name = traceInfo.methodName,
                context = SpanContext(
                    spanId = "0xce27bc8769488bd9",
                    traceId = "0xe432fc687caada44530eed8bccd3971b"
                ),
                parentId = null,
                startTime = getCurrentTimestamp(),
                endTime = getCurrentTimestamp().plus(10000),
                statusCode = "OK",
                attributes = Attributes(
                    traceRequestId = traceResponse.requestId,
                    spanFunctionName = traceInfo.methodName,
                    spanInputs = formatArgumentsAsJson(traceInfo.arguments),
                    spanOutputs = traceInfo.result.toString()
                ),
                events = emptyList()
            )

        ),
        request = formatArgumentsAsJson(traceInfo.arguments),
        response = traceInfo.result.toString()
    )

    client.put("${ML_FLOW_ARTIFACTS_API}/artifacts/${traceResponse.experimentId}/traces/${traceResponse.requestId}/artifacts/traces.json") {
        contentType(ContentType.Application.Json)
        setBody(spanArtifacts)
    }

    val tracePatchRequest = TracePatchRequest(
        requestId = traceResponse.requestId,
        status = "OK",
        requestMetadata = trace.requestMetadata +
            listOf(
                RequestMetadata("mlflow.traceInputs", formatArgumentsAsJson(traceInfo.arguments)),
                RequestMetadata("mlflow.traceOutputs", traceInfo.result.toString())
            ),
        tags = trace.tags
    )

    client.patch("${ML_FLOW_API}/traces/${traceResponse.requestId}") {
        contentType(ContentType.Application.Json)
        setBody(tracePatchRequest)
    }
}

suspend fun setTag(runId: String, key: String, value: String) {
    client.post("${ML_FLOW_API}/runs/set-tag") {
        contentType(ContentType.Application.Json)
        setBody(
            mapOf(
                "run_id" to runId, "run_uuid" to runId, "key" to key, "value" to value
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