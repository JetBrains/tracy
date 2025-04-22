package ai.dev.kit.providers.mlflow

import ai.dev.kit.core.eval.*
import ai.dev.kit.core.eval.model.*
import ai.dev.kit.core.fluent.FluentSpanAttributes
import ai.dev.kit.core.fluent.dataclasses.RunStatus
import ai.dev.kit.core.fluent.dataclasses.TraceInfo
import ai.dev.kit.core.fluent.processor.TracingFlowProcessor
import ai.dev.kit.providers.mlflow.dataclasses.*
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Scope
import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import java.io.File
import java.util.stream.Stream

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseEvaluationTest<
        AIInputT : AIInput,
        GroundTruthT : GroundTruth,
        AIOutputT : AIOutput,
        EvalResultT : EvalResult
        >(
    private val experimentName: String = "Evaluation test",
    private val runName: String? = null,
    private val numberOfRuns: Int = 1,
    private val tags: List<RunTag> = listOf(),
) {
    private lateinit var experimentId: String
    private var baselineText: String? = null
    private var modelData: ModelData? = null
    private val runResults = mutableListOf<RunResults<AIInputT, GroundTruthT, AIOutputT, EvalResultT>>()

    @BeforeAll
    fun beforeAll() {
        println("🔄 Setting up before all tests")

        TracingFlowProcessor.setupTracing(MlflowDiContainer.di)

        if (tags.isNotEmpty()) assertEquals(tags.size, numberOfRuns, "The number of tags must match the number of runs")

        experimentId = getExperimentByName(KotlinMlflowClient, experimentName)?.experimentId
            ?: createExperiment(KotlinMlflowClient, experimentName)
                    ?: throw IllegalStateException("Failed to create or retrieve experiment '$experimentName' at ${KotlinMlflowClient.ML_FLOW_URL}")

        KotlinMlflowClient.currentExperimentId = experimentId
        val baselineModelFile = getModelInfoFromFirstRun()

        modelData = createModelData()

        baselineText = baselineModelFile?.readText() ?: Json.encodeToString(modelData)

        val runNameBase = runName ?: runBlocking { createRunName() }

        (1..numberOfRuns).map { runNum ->
            runBlocking {
                val runId = createRun(
                    KotlinMlflowClient,
                    runNameBase + if (numberOfRuns > 1) runNum else "",
                    experimentId
                )?.runId.toString()

                KotlinMlflowClient.currentRunId = runId

                modelData?.runId = runId
                setupMlflow(modelData, runId)

                runResults.add(RunResults(mutableListOf(), runId, RunStatus.FINISHED))
            }
        }
    }

    private fun getModelInfoFromFirstRun(): File? {
        val allRuns = KotlinMlflowClient.listRunInfos(experimentId)
        val firstRun = allRuns.lastOrNull() ?: return null

        return try {
            KotlinMlflowClient.downloadArtifacts(firstRun.runId, "model/MLmodel")
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
        val metadata = generator.metadata

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
                prompt = metadata.prompt,
                model = metadata.modelName,
                temperature = metadata.temperature,
            ),
            utcTimeCreated = getCurrentTimestamp().toString()
        )

        return modelData
    }

    private suspend fun setupMlflow(modelData: ModelData?, runId: String) {
        println("🚀 MLFlow setup for run: $runId")
        modelData ?: return

        logModel(runId, modelJson = createModelJson(modelData))

        val loggedRun = getRun(runId)
        val modelArtifactPath = "${loggedRun.info.experimentId}/${runId}/artifacts/model/MLmodel"

        uploadArtifact(modelArtifactPath, createModelYaml(modelData))
    }

    @AfterAll
    fun afterAll() {
        println("📊 Logging evaluation results")

        runResults.forEachIndexed { runIndex, runResult ->
            val (testResults, runId, runStatus) = runResult
            try {
                runBlocking {
                    if (tags.isNotEmpty())
                        setTag(
                            runId,
                            "mlflow.runColor",
                            tags[runIndex].color,
                        )
                }
                uploadResultsTableAsArtifact(runId, testResults)
                logAverageScore(runId, testResults.map { it.evalResult })
            } catch (e: Exception) {
                runResults[runIndex].finalStatus = RunStatus.FAILED
            } finally {
                runBlocking { updateRun(runId, runStatus) }
            }
        }
    }

    @TestFactory
    fun Runs(): Stream<DynamicContainer> = runResults.mapIndexed { runNum, runResult ->
        DynamicContainer.dynamicContainer(
            "Run ${if (runResults.size > 1) runNum + 1 else ""}",
            testCases.mapIndexed { dataPointIndex, testCase ->
                val (dataPointSpan, dataPointScope) =
                    createDataPointSpan(dataPointIndex, TracingFlowProcessor.tracer, runResult.runId, testCase)
                val output = runBlocking {
                    withContext(dataPointSpan.asContextElement()) {
                        generator.generate(testCase.input)
                    }
                }
                val testCaseName = testCase.name
                DynamicTest.dynamicTest(testCaseName) {
                    dataPointSpan.makeCurrent()
                    try {
                        executeSingleTest(testCaseName, evaluator, testCase, runNum, runResult.runId, output)
                    } finally {
                        dataPointSpan.end()
                        dataPointScope.close()
                    }
                }
            }
        )
    }.stream()

    private fun createDataPointSpan(
        dataPointIndex: Int,
        tracer: Tracer,
        runId: String,
        testCase: TestCase<AIInputT, GroundTruthT>
    ): Pair<Span, Scope> {
        val tracedRunName = "Data Point ${dataPointIndex + 1}"
        val dataPointSpan = tracer.spanBuilder(tracedRunName).setNoParent().also {
            runBlocking {
                val tracePostRequest = createTracePostRequest(
                    experimentId = experimentId,
                    runId = runId,
                    traceCreationPath = "No path for root test",
                    traceName = tracedRunName
                )

                val jsonTraceInfo = Json.encodeToString(TraceInfo.serializer(), createTrace(tracePostRequest))
                it.setAttribute(FluentSpanAttributes.TRACE_CREATION_INFO.key, jsonTraceInfo)
                it.setAttribute(
                    FluentSpanAttributes.SPAN_INPUTS.key,
                    "{\"Data Point\": \"${testCase.input}\"}"
                )
            }
        }.startSpan()
        val dataPointScope = dataPointSpan.makeCurrent()
        return dataPointSpan to dataPointScope
    }

    private fun executeSingleTest(
        testCaseName: String,
        testFunction: Evaluator<GroundTruthT, AIOutputT, EvalResultT>,
        testCase: TestCase<AIInputT, GroundTruthT>,
        runNum: Int,
        runId: String,
        output: AIOutputT
    ) {
        val result = try {
            testFunction.evaluate(testCase.groundTruth, output)
        } catch (e: Throwable) {
            val message = "❌ Test Failed: $testCaseName | Case: $testCase | Reason: Evaluator has thrown ${e.message}"
            logTest(message, runId)
            fail(message)
        }
        runResults[runNum].testResults.add(TestResult(testCase, output, result))

        if (result.hasJunitTestSucceeded) {
            logTest(
                message = "✅ Test Passed: $testCaseName | Case: $testCase | Result: $result",
                runId = runId
            )
        } else {
            val message = "❌ Test failed: $testCaseName | Case: $testCase | Result: $result"
            logTest(message, runId)
            fail(message)
        }
    }

    private fun logTest(message: String, runId: String) {
        println(message)
        println("🔗 View results at ${KotlinMlflowClient.ML_FLOW_URL}/#/experiments/$experimentId/runs/$runId")
    }

    private fun uploadResultsTableAsArtifact(
        runId: String,
        testResults: List<TestResult<AIInputT, GroundTruthT, AIOutputT, EvalResultT>>
    ) {
        val loggedRun = runBlocking { getRun(runId) }
        val artifactPath = "${loggedRun.info.experimentId}/${runId}/artifacts/eval_results_table.json"

        val table = testResults.toTable() ?: return
        uploadArtifact(artifactPath, table.dumpForMLFlow())

        runBlocking {
            setTag(
                runId,
                "mlflow.loggedArtifacts",
                "[{\"path\": \"eval_results_table.json\", \"type\": \"table\"}]"
            )
        }
    }

    private fun logAverageScore(runId: String, evalResults: List<EvalResultT>) {
        val aggregateScores = evaluator.aggregateResults(evalResults)
        for (agg in aggregateScores) {
            logMetric(KotlinMlflowClient, runId, agg.scoreName, agg.score)
        }
        if (aggregateScores.isNotEmpty()) {
            println("📈 Results logged to MLFlow for Run ID: $runId")
        }
    }

    abstract val testCases: List<TestCase<AIInputT, GroundTruthT>>
    abstract val generator: Generator<AIInputT, AIOutputT>
    abstract val evaluator: Evaluator<GroundTruthT, AIOutputT, EvalResultT>
}

private data class RunResults<
        AIInputT : AIInput,
        GroundTruthT : GroundTruth,
        AIOutputT : AIOutput,
        EvalResultT : EvalResult
        >(
    val testResults: MutableList<TestResult<AIInputT, GroundTruthT, AIOutputT, EvalResultT>>,
    val runId: String,
    var finalStatus: RunStatus,
)
