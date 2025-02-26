package ai.mlflow.logging

import kotlinx.coroutines.runBlocking
import org.example.ai.mlflow.*
import org.example.ai.model.*
import org.junit.jupiter.api.Test
import org.mlflow.tracking.MlflowClient
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

private const val EXPERIMENT_ID = "259381197825368132"

class TestLogging {
    @Test
    fun testCreateRun() {
        runBlocking {
            val run = createRun("TestLogging.testCreateRun", EXPERIMENT_ID)
            val runId = run.info.runId
            updateRun(runId, RunStatus.FINISHED)
        }
    }

    @Test
    fun testLogModel() {
        runBlocking {
            val run = createRun("TestLogging.testLogModel", EXPERIMENT_ID)
            val runId = run.info.runId

            val modelData = ModelData(
                runId = runId,
                artifactPath = "model",
                flavors = Flavors(
                    openai = OpenAI(
                        openaiVersion = "1.60.2",
                        data = "model.yaml",
                        code = "print(0)"
                    )
                ),
                signature = Signature(
                    inputs = "[{\"type\": \"string\", \"required\": true}]",
                    outputs = "[{\"type\": \"string\", \"required\": true}]",
                    params = null
                )
            )

            logModel(runId, modelJson = createModelJson(modelData))

            val loggedRun = getRun(runId)
            val artifactUri = loggedRun.info.artifactUri

            logModelData(artifactUri, modelData)

            logBatch(
                runId = runId,
                metrics = listOf(
                    Metric(
                        "metric",
                        1.0
                    )
                )
            )

            setTag(
                runId,
                "mlflow.loggedArtifacts",
                "[{\"path\": \"eval_results_table.json\", \"type\": \"table\"}]"
            )

            updateRun(runId, RunStatus.FINISHED)
        }
    }

    @Test
    fun testGetRun() {
        runBlocking {
            val mlFlowClient = MlflowClient("http://localhost:5001")

            val run = createRun(mlFlowClient, "TestLogging.testLogModel", EXPERIMENT_ID)
            assertNotNull(run, "Run was not created")

            val runData = mlFlowClient.getRun(run.runId)
            assertEquals(run, runData.info)

            mlFlowClient.close()
        }
    }

    @Test
    fun testCreateExperiment() {
        runBlocking {
            val experiment = createExperiment("New Experiment")
            val experimentData = getExperiment(experiment)

            assertEquals(experiment, experimentData.experimentId)
        }
    }
}