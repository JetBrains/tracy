package org.example.ai.mlflow

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.example.ai.AIModel
import org.example.ai.createAIClient
import org.example.ai.mlflow.dataclasses.*
import org.example.ai.mlflow.dataclasses.TestInfo
import org.example.ai.mlflow.fluent.KotlinFlowTrace
import org.example.ai.mlflow.fluent.processor.TracingFlowProcessor
import org.example.ai.model.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.mlflow.tracking.MlflowClient
import java.io.File
import java.util.stream.Stream

/**
 * @param I The input to the model.
 * @param O The output produced by the model.
 * @param R The evaluation result produced based on the model output.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseEvaluationTest<I, O, R>(
    private val experimentName: String = "Evaluation test",
    private val runName: String? = null,
    private val numberOfRuns: Int = 1,
    private val tags: List<RunTag> = listOf(),
    private val baseUrl: String = "http://localhost:5001",
    private var artifactLocation: String? = null
) {
    private lateinit var mlFlowClient: MlflowClient

    private lateinit var experimentId: String
    private var baselineText: String? = null
    private var modelData: ModelData? = null
    private val runResults = mutableListOf<RunResults<I, O, R>>()

    @BeforeAll
    fun beforeAll() {
        println("🔄 Setting up before all tests")

        TracingFlowProcessor.setup()
        MlflowClients.setExperimentByName(experimentName)

        if (tags.isNotEmpty()) assertEquals(tags.size, numberOfRuns, "The number of tags must match the number of runs")

        mlFlowClient = MlflowClient(baseUrl)

        if(artifactLocation == null) artifactLocation = getMLflowRunsDir()

        experimentId = getExperimentByName(mlFlowClient, experimentName)?.experimentId
            ?: createExperiment(mlFlowClient, experimentName, artifactLocation!!)
                    ?: throw IllegalStateException("Failed to create or retrieve experiment '$experimentName' at $baseUrl")

        val baselineModelFile = getModelInfoFromFirstRun()

        modelData = createModelData()

        baselineText = baselineModelFile?.readText() ?: Json.encodeToString(modelData)

        val runNameBase = runName ?: runBlocking { createRunName() }

        (1..numberOfRuns).map { runNum ->
            runBlocking {
                val runId = createRun(
                    mlFlowClient,
                    runNameBase + if (numberOfRuns > 1) runNum else "",
                    experimentId
                )?.runId.toString()

                MlflowClients.currentRunId = runId

                    modelData?.runId = runId
                setupMlflow(modelData, runId)

                runResults.add(RunResults(mutableListOf(), runId, RunStatus.FINISHED))
            }
        }
    }

    private fun getModelInfoFromFirstRun(): File? {
        val allRuns = mlFlowClient.listRunInfos(experimentId)
        val firstRun = allRuns.lastOrNull() ?: return null

        return try {
            mlFlowClient.downloadArtifacts(firstRun.runId, "model/MLmodel")
        } catch (e: Exception) {
            println("Warning: Failed to download model info from the first run")
            null
        }
    }

    private suspend fun createRunName(): String {
        val promptRules = """
        Generate a concise and meaningful run name based on the following model parameters.

        The rules are:
        - Output exactly one run name with no explanations or additional text.
        - The name must have at most five words.
        - Use camel case or underscores if needed.
    """
        val modelDataJson = Json.encodeToString(modelData)

        val prompt = buildString {
            append(promptRules)
            append("\n\nBaseline model parameters: $baselineText")
            if (baselineText != modelDataJson) {
                append("\nCurrent model parameters: $modelDataJson")
                append("\nBase your name on diff between the Baseline and Current parameters")
            }
        }

        val client = createAIClient()
        return client.chatRequest(AIModel.GPT_4O_MINI, prompt, temperature = 1.0)
    }

    private fun createModelData(runId: String = "<PLACEHOLDER_RUN_ID>"): ModelData {
        val modelData = ModelData(
            runId = runId,
            artifactPath = "model",
            flavors = Flavors(
                openai = OpenAI(
                    openaiVersion = "1.60.2",
                    data = "model.yaml",
                    code = ""
                )
            ),
            signature = Signature(
                inputs = "[{\"type\": \"string\", \"required\": true}]",
                outputs = "[{\"type\": \"string\", \"required\": true}]",
                params = null
            ),
            modelParameters = ModelParameters(
                prompt = model().prompt,
                model = model().model.name,
                temperature = model().temperature
            )
        )

        return modelData
    }

    private suspend fun setupMlflow(modelData: ModelData?, runId: String) {
        println("🚀 MLFlow setup for run: $runId")
        modelData ?: return

        logModel(runId, modelJson = createModelJson(modelData))

        val loggedRun = getRun(runId)
        val artifactUri = loggedRun.info.artifactUri

        logModelData(artifactUri, modelData)
    }

    @AfterAll
    fun afterAll() {
        println("📊 Logging evaluation results")

        runResults.forEachIndexed { index, runResult ->
            val (testResults, runId, runStatus) = runResult

            runBlocking {
                updateRun(runId, runStatus)

                if (tags.isNotEmpty())
                    setTag(
                        runId,
                        "mlflow.runColor",
                        tags[index].color,
                    )
            }

            val metricsLookup = testResults.associate { (it.testName to it.input) to (it.result to it.output) }

            val testNames = testResults.map { it.testName }.distinct()
            val testCases = testResults.map { it.input }.distinct()

            val tableData: List<List<String>> = testCases.map { testCase ->
                val output = metricsLookup[testNames.firstOrNull() to testCase]?.second
                listOf(testCase.toString(), output.toString()) + testNames.map { testName ->
                    val resultInfo = metricsLookup[testName to testCase]
                    resultInfo?.first?.toString() ?: "N/A"
                }
            }

            val evalResultsTable = EvalResultsTable(
                columns = listOf("inputs", "output") + testNames,
                data = tableData
            )

            val evalResultsJson = Json.encodeToString(evalResultsTable)
            val artifactUri = "${mlFlowClient.getRun(runId).info.artifactUri}/eval_results_table.json"

            logArtifact(artifactUri, evalResultsJson)

            runBlocking {
                setTag(
                    runId,
                    "mlflow.loggedArtifacts",
                    "[{\"path\": \"eval_results_table.json\", \"type\": \"table\"}]"
                )
            }

            logAveragePlot(runId, testResults)
        }
    }


    @TestFactory
    @KotlinFlowTrace(name = "Evaluation")
    fun Runs(): Stream<DynamicContainer> =
        runResults.mapIndexed { runNum, runResult ->
            DynamicContainer.dynamicContainer(
                "Run ${if (runResults.size > 1) runNum + 1 else ""}", testFunctions().map { testFunction ->
                    DynamicContainer.dynamicContainer(testFunction.name, testCases().map { testCase ->
                        val output = runBlocking { model().generate(testCase.input) }

                        DynamicTest.dynamicTest(testCase.input.toString()) {
                            executeSingleTest(testFunction, testCase, runNum, runResult.runId, output)
                        }
                    })
                })
        }.stream()

    @KotlinFlowTrace(name = "Test")
    private fun executeSingleTest(
        testFunction: EvaluationCriteria<O, R>,
        testCase: TestCase<I, R>,
        runNum: Int,
        runId: String,
        output: O
    ) {
        var result: R? = null
        var message = "⚠️ Run details are missing or unavailable."

        try {
            result = testFunction.evaluate(output)

            assertEquals(
                testCase.expected,
                result
            )

            message = "✅ Test Passed: ${testFunction.name} | Case: ${testCase.input}"
        } catch (e: Throwable) {
            runResults[runNum].finalStatus = RunStatus.FAILED
            message = "❌ Test Failed: ${testFunction.name} | Case: ${testCase.input} | Reason: ${e.message}"
            throw e
        } finally {
            logTest(message, runId)

            if (result != null && output != null) {
                runResults[runNum].testResults.add(TestInfo(testCase.input, output, result, testFunction.name))
            }
        }
    }

    private fun logTest(message: String, runId: String) {
        println(message)
        println("🔗 View results at ${baseUrl}/#/experiments/$experimentId/runs/$runId")
    }

    private fun logAveragePlot(runId: String, testResults: List<TestInfo<I, O, R>>) {
        runBlocking {
            val results = testResults.map { it.result }

            if (results.all { it is Number }) {
                val avgScore = results.map { (it as Number).toDouble() }.average()

                testResults.forEach { test ->
                    logMetric(mlFlowClient, runId, test.testName, test.result as Double)
                }

                logMetric(mlFlowClient, runId, "Overall_Average", avgScore)
            } else {
                println("Warning: Results are not numeric, cannot compute average.")
            }

            println("📈 Results logged to MLFlow for Run ID: $runId")
        }
    }

    open fun getMLflowRunsDir(): String {
        return getDefaultArtifactLocation(mlFlowClient)
    }

    abstract fun testCases(): List<TestCase<I, R>>

    abstract fun model(): Generator<I, O>

    abstract fun testFunctions(): List<EvaluationCriteria<O, R>>
}