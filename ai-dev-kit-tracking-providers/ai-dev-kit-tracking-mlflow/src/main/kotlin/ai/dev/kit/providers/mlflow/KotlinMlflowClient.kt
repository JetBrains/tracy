package ai.dev.kit.providers.mlflow

import ai.dev.kit.core.fluent.KotlinLoggingClient
import ai.dev.kit.core.fluent.getUserID
import ai.dev.kit.core.fluent.dataclasses.RunStatus
import ai.dev.kit.providers.mlflow.KotlinMlflowClient.MLFLOW_HOST
import ai.dev.kit.providers.mlflow.KotlinMlflowClient.MLFLOW_PORT
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import org.mlflow.api.proto.Service
import org.mlflow.tracking.MlflowClient
import java.util.logging.LogManager
import java.util.logging.Logger

internal const val ML_FLOW_URL = "http://$MLFLOW_HOST:$MLFLOW_PORT"

internal object KotlinMlflowClient : MlflowClient(ML_FLOW_URL), KotlinLoggingClient {
    private val logger: Logger = LogManager.getLogManager().getLogger(Logger.GLOBAL_LOGGER_NAME)
        ?: Logger.getLogger(KotlinMlflowClient::class.java.name)

    internal const val MLFLOW_VERSION = "2.20.2"
    internal const val MLFLOW_HOST = "127.0.0.1"
    internal const val MLFLOW_PORT = 5002
    internal const val ML_FLOW_API = "$ML_FLOW_URL/api/2.0/mlflow"
    internal const val ML_FLOW_ARTIFACTS_API = "$ML_FLOW_URL/api/2.0/mlflow-artifacts"
    override val USER_ID: String = getUserID()

    // TODO: Remove state storage here ASAP!
    override var currentExperimentId: String = "0"
    override var currentRunId: String? = null

    val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
    }

    override fun createRun(experimentId: String): Service.RunInfo? {
        return super.createRun(experimentId).also {
            currentRunId = it?.runId
        }
    }

    override fun withRun(experimentId: String) = object : AutoCloseable {
        val myRunId = createRun(experimentId)?.runId

        override fun close() {
            // TODO GET RID OF RUN BLOCKING
            runBlocking {
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
                logger.info("Experiment with name $experimentName not found, creating a new one")
                this.createExperiment(experimentName)
            }
        } catch (e: Exception) {
            logger.warning("Unexpected error occurred when setting experiment by name: ${e.message}")
        }
    }
}
