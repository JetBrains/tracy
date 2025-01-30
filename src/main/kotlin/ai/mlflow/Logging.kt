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
import java.time.Instant

private const val ML_FLOW_API = "http://localhost:5000/api/2.0/mlflow"
private const val EXPERIMENT_ID = "400879250576949711"
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
    @SerialName("tags") val tags: List<String> = emptyList()
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
data class Tag(
    @SerialName("key") val key: String,
    @SerialName("value") val value: String
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

private fun getCurrentTimestamp(): Long {
    return Instant.now().toEpochMilli()
}

suspend fun createRun(name: String): RunResponse {
    val run = RunCreationData(
        experimentId = EXPERIMENT_ID,
        userId = USER_ID,
        runName = name,
        startTime = getCurrentTimestamp()
    )

    val result = client.post("${ML_FLOW_API}/runs/create") {
        contentType(ContentType.Application.Json)
        setBody(run)
    }

    val runResult = kotlinx.serialization.json.Json.decodeFromString<RunResponse>(result.bodyAsText())
    return runResult
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

suspend fun getExperiment() {
    val response: HttpResponse = client.get("${ML_FLOW_API}/experiments/get") {
        contentType(ContentType.Application.Json)
        setBody(mapOf("experiment_id" to EXPERIMENT_ID))
    }

    println(response.bodyAsText())
    client.close()
}