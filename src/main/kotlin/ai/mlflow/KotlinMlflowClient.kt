package org.example.ai.mlflow

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import org.example.ai.mlflow.KotlinMlflowClient.ML_FLOW_URL
import org.mlflow.api.proto.Service
import org.mlflow.tracking.MlflowClient
import java.util.logging.LogManager
import java.util.logging.Logger

internal object KotlinMlflowClient : MlflowClient(ML_FLOW_URL) {
    private val logger: Logger = LogManager.getLogManager().getLogger(Logger.GLOBAL_LOGGER_NAME)
        ?: Logger.getLogger(KotlinMlflowClient::class.java.name)

    private const val ML_FLOW_URL = "http://localhost:8080"
    const val ML_FLOW_API = "$ML_FLOW_URL/api/2.0/mlflow"
    const val ML_FLOW_ARTIFACTS_API = "$ML_FLOW_URL/api/2.0/mlflow-artifacts"
    const val USER_ID = "Anton.Bragin"

    internal var currentExperimentId: String = "0"
        private set

    internal var currentRunId: String? = null
        private set

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

    override fun createRun(): Service.RunInfo? {
        return super.createRun(currentExperimentId).also {
            currentRunId = it?.runId
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
