package ai.dev.kit.providers.mlflow

import ai.dev.kit.providers.mlflow.KotlinMlflowClient.ML_FLOW_URL
import ai.dev.kit.eval.utils.getUserIDFromEnv
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import org.mlflow.tracking.MlflowClient

internal object KotlinMlflowClient : MlflowClient(ML_FLOW_URL) {
    internal const val MLFLOW_VERSION = "2.20.2"
    internal const val MLFLOW_HOST = "127.0.0.1"
    internal const val MLFLOW_PORT = 5002
    internal const val ML_FLOW_URL = "http://$MLFLOW_HOST:$MLFLOW_PORT"
    internal const val ML_FLOW_API = "$ML_FLOW_URL/api/2.0/mlflow"
    internal const val ML_FLOW_ARTIFACTS_API = "$ML_FLOW_URL/api/2.0/mlflow-artifacts"
    // Mlflow support uses mlflow rest api
    // docs: https://mlflow.org/docs/latest/api_reference/rest-api.html

    val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
    }

    internal fun setupCredentials(userId: String?) {
        USER_ID = userId ?: getUserIDFromEnv()
    }

    internal lateinit var USER_ID: String
}
