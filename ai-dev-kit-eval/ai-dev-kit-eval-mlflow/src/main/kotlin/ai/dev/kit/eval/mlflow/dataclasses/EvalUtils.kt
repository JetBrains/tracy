package ai.dev.kit.eval.mlflow.dataclasses

import kotlinx.serialization.Serializable
import ai.dev.kit.eval.base.AIModel
import ai.dev.kit.eval.mlflow.RunStatus
import ai.dev.kit.eval.mlflow.getExperiment
import ai.dev.kit.eval.mlflow.getExperimentByName
import org.mlflow.tracking.MlflowClient
import java.nio.file.Paths


data class TestCase<I>(
    val input: I
)

abstract class EvaluationCriteria<O, R>(val name: String, val resultExpected: R? = null) {
    abstract fun evaluate(output: O): R
}

interface Generator<I, O> {
    suspend fun generate(input: I): O
    val prompt: String
    val temperature: Double
    val model: AIModel
}

data class RunTag(
    val color: String
)

@Serializable
data class EvalResultsTable(
    val columns: List<String>,
    val data: List<List<String>>
)

data class TestInfo<I, O, R>(
    val input: I,
    val output: O,
    val result: R,
    val testName: String
)

data class RunResults<I, O, R>(
    val testResults: MutableList<TestInfo<I, O, R>>,
    val runId: String,
    var finalStatus: RunStatus,
)

fun getDefaultArtifactLocation(mlflowClient: MlflowClient): String {
    val defaultLocation = "file:///tmp/mlflow/artifacts/"
    val warningMessage = "Warning: Unable to fetch artifact location from MLFlow. Using fallback."

    return try {
        val experiment = getExperimentByName(mlflowClient, "Default")
            ?: getExperiment(mlflowClient, mlflowClient.createExperiment("Default"))
            ?: run {
                println(warningMessage)
                return defaultLocation
            }
        "file://${Paths.get(experiment.artifactLocation).parent}/"
    } catch (e: Exception) {
        println(warningMessage)
        defaultLocation
    }
}
