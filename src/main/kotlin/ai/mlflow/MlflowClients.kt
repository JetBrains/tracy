package org.example.ai.mlflow

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import org.mlflow.tracking.MlflowClient
import java.util.logging.LogManager
import java.util.logging.Logger

internal object MlflowClients {
    private val logger: Logger = LogManager.getLogManager().getLogger(Logger.GLOBAL_LOGGER_NAME)
        ?: Logger.getLogger(MlflowClients::class.java.name)

    private const val ML_FLOW_URL = "http://localhost:5001"
    const val ML_FLOW_API = "$ML_FLOW_URL/api/2.0/mlflow"
    const val ML_FLOW_ARTIFACTS_API = "$ML_FLOW_URL/api/2.0/mlflow-artifacts"
    const val USER_ID = "Anton.Bragin"

    internal var currentExperimentId: String = "0"
        private set

    internal var currentRunId: String? = null

    val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
    }

    val defaultMLFlowClient = MlflowClient(ML_FLOW_URL)


    fun setExperimentByName(experimentName: String) {
        try {
            val currentExperiment = defaultMLFlowClient.getExperimentByName(experimentName)
            currentExperimentId = if (currentExperiment.isPresent) {
                currentExperiment.get().experimentId
            } else {
                logger.info("Experiment with name $experimentName not found, creating a new one")
                defaultMLFlowClient.createExperiment(experimentName)
            }
        } catch (e: Exception) {
            logger.warning("Unexpected error occurred when setting experiment by name: ${e.message}")
        }
    }

}
