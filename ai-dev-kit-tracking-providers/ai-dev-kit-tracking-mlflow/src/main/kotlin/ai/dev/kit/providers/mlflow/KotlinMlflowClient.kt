package ai.dev.kit.providers.mlflow

import ai.dev.kit.providers.mlflow.KotlinMlflowClient.ML_FLOW_URL
import ai.dev.kit.tracing.fluent.KotlinLoggingClient
import ai.dev.kit.tracing.fluent.dataclasses.RunStatus
import ai.dev.kit.tracing.fluent.getUserIDFromEnv
import ai.dev.kit.tracing.fluent.processor.TracingFlowProcessor
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.mlflow.api.proto.Service
import org.mlflow.tracking.MlflowClient

internal object KotlinMlflowClient : MlflowClient(ML_FLOW_URL), KotlinLoggingClient {
    private val logger = KotlinLogging.logger {}

    internal const val MLFLOW_VERSION = "2.20.2"
    internal const val MLFLOW_HOST = "127.0.0.1"
    internal const val MLFLOW_PORT = 5002
    internal const val ML_FLOW_URL = "http://$MLFLOW_HOST:$MLFLOW_PORT"
    internal const val ML_FLOW_API = "$ML_FLOW_URL/api/2.0/mlflow"
    internal const val ML_FLOW_ARTIFACTS_API = "$ML_FLOW_URL/api/2.0/mlflow-artifacts"
    // Mlflow support uses mlflow rest api
    // docs: https://mlflow.org/docs/latest/api_reference/rest-api.html

    // TODO: Remove state storage here ASAP!
    override var currentExperimentId: String = "0"
    var currentRunId: String? = null

    val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
    }

    internal fun setupCredentials(userId: String?) {
        USER_ID = userId ?: getUserIDFromEnv()
    }

    internal lateinit var USER_ID: String

    override fun createRun(experimentId: String): Service.RunInfo? {
        return super.createRun(experimentId).also {
            currentRunId = it?.runId
        }
    }

    override fun withRun(experimentId: String) = object : AutoCloseable {
        val myRunId = createRun(experimentId)?.runId

        override fun close() {
            TracingFlowProcessor.scope.launch {
                myRunId?.let { updateRun(myRunId, RunStatus.FINISHED) }
            }
        }
    }

    fun setExperimentByName(experimentName: String) {
        try {
            val currentExperiment = this.getExperimentByName(experimentName)
            currentExperimentId = if (currentExperiment.isPresent) {
                currentExperiment.get().experimentId
            } else {
                logger.info{"Experiment with name $experimentName not found, creating a new one"}
                this.createExperiment(experimentName)
            }
        } catch (e: Exception) {
            logger.warn{"Unexpected error occurred when setting experiment by name: ${e.message}"}
        }
    }
}
