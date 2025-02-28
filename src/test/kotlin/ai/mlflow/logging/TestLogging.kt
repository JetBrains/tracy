package ai.mlflow.logging

import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.Ports
import kotlinx.coroutines.runBlocking
import org.example.ai.mlflow.*
import org.example.ai.model.*
import org.junit.jupiter.api.*
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.containers.wait.strategy.Wait
import org.mlflow.tracking.MlflowClient
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

private const val MLFLOW_PORT = 5001
private const val MLFLOW_VERSION = "2.10.0"

@Testcontainers
class TestLogging {

    private lateinit var experimentId: String

    @BeforeEach
    fun setupTestData() {
        runBlocking {
            experimentId = createTestExperiment()
        }
    }

    companion object {
        @Container
        val mlflowContainer: GenericContainer<*> = GenericContainer("ghcr.io/mlflow/mlflow:v$MLFLOW_VERSION")
            .withExposedPorts(8090)
            .withCommand("mlflow server --host 0.0.0.0 --port 8090")
            .waitingFor(Wait.forListeningPort())
            .withCreateContainerCmdModifier { cmd ->
                cmd.withHostConfig(
                    HostConfig().withPortBindings(
                        Ports.Binding.bindPort(MLFLOW_PORT).let { binding ->
                            Ports().apply { bindings[ExposedPort.tcp(8090)] = arrayOf(binding) }
                        }
                    )
                )
            }

        @BeforeAll
        @JvmStatic
        fun setup() {
            mlflowContainer.start()
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            mlflowContainer.stop()
        }
    }

    @Test
    fun testCreateExperiment() {
        runBlocking {
            val experimentData = getExperiment(experimentId)
            assertEquals(experimentId, experimentData.experimentId)
        }
    }

    @Test
    fun testCreateRun() {
        runBlocking {
            val run = createRun("TestLogging.testCreateRun", experimentId)
            val runId = run.info.runId
            updateRun(runId, RunStatus.FINISHED)
        }
    }

    @Test
    fun testLogModel() {
        runBlocking {
            val run = createRun("TestLogging.testLogModel", experimentId)
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

            val run = createRun(mlFlowClient, "TestLogging.testLogModel", experimentId)
            assertNotNull(run, "Run was not created")

            val runData = mlFlowClient.getRun(run.runId)
            assertEquals(run, runData.info)

            mlFlowClient.close()
        }
    }

    private suspend fun createTestExperiment(): String {
        val experimentName = "Test Experiment ${System.currentTimeMillis()}"
        return createExperiment(experimentName)
    }
}