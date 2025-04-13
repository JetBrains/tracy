package ai.dev.kit.eval.mlflow

import ai.dev.kit.core.fluent.processor.TracingFlowProcessor
import ai.dev.kit.eval.mlflow.dataclasses.EvalResultsTable
import ai.dev.kit.eval.mlflow.dataclasses.EvaluationCriteria
import ai.dev.kit.eval.mlflow.dataclasses.Generator
import ai.dev.kit.eval.mlflow.dataclasses.RunResults
import ai.dev.kit.eval.mlflow.dataclasses.RunTag
import ai.dev.kit.eval.mlflow.dataclasses.TestCase
import ai.dev.kit.eval.mlflow.fluent.MlflowFluentSpanAttributes
import ai.dev.kit.eval.mlflow.fluent.MlflowTracePublisher
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Scope
import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ai.dev.kit.eval.base.AIModel
import ai.dev.kit.eval.base.createAIClient
import ai.dev.kit.eval.base.model.Flavors
import ai.dev.kit.eval.base.model.ModelData
import ai.dev.kit.eval.base.model.ModelParameters
import ai.dev.kit.eval.base.model.OpenAI
import ai.dev.kit.eval.base.model.Signature
import ai.dev.kit.eval.base.model.createModelJson
import ai.dev.kit.eval.base.model.createModelYaml
import ai.dev.kit.eval.mlflow.dataclasses.TestInfo
import ai.dev.kit.eval.mlflow.dataclasses.TraceInfo
import ai.dev.kit.eval.mlflow.dataclasses.createTracePostRequest
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import java.io.File
import java.util.stream.Stream
import kotlin.collections.get

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
) {
    private lateinit var experimentId: String
    private var baselineText: String? = null
    private var modelData: ModelData? = null
    private val runResults = mutableListOf<RunResults<I, O, R>>()

    @BeforeAll
    fun beforeAll() {
        println("🔄 Setting up before all tests")

        TracingFlowProcessor.setupTracing(MlflowTracePublisher)

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

        runResults.forEachIndexed { index, runResult ->
            val (testResults, runId, runStatus) = runResult
            try {
                runBlocking {
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

                val loggedRun = runBlocking { getRun(runId) }
                val artifactPath = "${loggedRun.info.experimentId}/${runId}/artifacts/eval_results_table.json"

                uploadArtifact(artifactPath, evalResultsJson)

                runBlocking {
                    setTag(
                        runId,
                        "mlflow.loggedArtifacts",
                        "[{\"path\": \"eval_results_table.json\", \"type\": \"table\"}]"
                    )
                }

                logAveragePlot(runId, testResults)
            } catch (e: Exception) {
                runResults[index].finalStatus = RunStatus.FAILED
            } finally {
                runBlocking { updateRun(runId, runStatus) }
            }
        }
    }


    @TestFactory
    fun Runs(): Stream<DynamicContainer> = runResults.mapIndexed { runNum, runResult ->
        val testFunctions = testFunctions()

        DynamicContainer.dynamicContainer(
            "Run ${if (runResults.size > 1) runNum + 1 else ""}",
            testCases().mapIndexed { dataPointIndex, testCase ->
                val (dataPointSpan, dataPointScope) =
                    createDataPointSpan(dataPointIndex, TracingFlowProcessor.tracer, runResult.runId, testCase)
                val output = runBlocking {
                    withContext(dataPointSpan.asContextElement()) {
                        model().generate(testCase.input)
                    }
                }
                DynamicContainer.dynamicContainer(
                    testCase.input.toString(),
                    testFunctions.mapIndexed { index, testFunction ->
                        DynamicTest.dynamicTest(testFunction.name) {
                            executeSingleTest(dataPointSpan, testFunction, testCase, runNum, runResult.runId, output)
                            if (index == testFunctions.lastIndex) {
                                dataPointSpan.end()
                                dataPointScope.close()
                            }
                        }
                    }
                )
            }
        )
    }.stream()

    private fun createDataPointSpan(dataPointIndex: Int,
                                     tracer: Tracer,
                                     runId: String,
                                     testCase: TestCase<I>
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
                it.setAttribute(MlflowFluentSpanAttributes.TRACE_CREATION_INFO.asAttributeKey(), jsonTraceInfo)
                it.setAttribute(
                    MlflowFluentSpanAttributes.MLFLOW_SPAN_INPUTS.asAttributeKey(),
                    "{\"Data Point\": \"${testCase.input}\"}"
                )
            }
        }.startSpan()
        val dataPointScope = dataPointSpan.makeCurrent()
        return dataPointSpan to dataPointScope
    }

    private fun executeSingleTest(
        span: Span,
        testFunction: EvaluationCriteria<O, R>,
        testCase: TestCase<I>,
        runNum: Int,
        runId: String,
        output: O
    ) {
        var result: R? = null
        var message = "⚠️ Run details are missing or unavailable."

        try {
            span.makeCurrent()

            result = testFunction.evaluate(output)

            message = if (testFunction.resultExpected != null) {
                assertEquals(
                    testFunction.resultExpected,
                    result
                )
                "✅ Test Passed: ${testFunction.name} | Case: ${testCase.input} | Result: $result"
            } else {
                "🎯 Test Executed: ${testFunction.name} | Case: ${testCase.input} | Result: $result"
            }


        } catch (e: Throwable) {
            message =
                "❌ Test Failed: ${testFunction.name} | Case: ${testCase.input} | Result: $result | Reason: ${e.message}"
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
        println("🔗 View results at ${KotlinMlflowClient.ML_FLOW_URL}/#/experiments/$experimentId/runs/$runId")
    }

    private fun logAveragePlot(runId: String, testResults: List<TestInfo<I, O, R>>) {
        runBlocking {
            val results = testResults.map { it.result }

            if (results.all { it is Number }) {
                val avgScore = results.map { (it as Number).toDouble() }.average()

                testResults.forEach { test ->
                    logMetric(KotlinMlflowClient, runId, test.testName, test.result as Double)
                }

                logMetric(KotlinMlflowClient, runId, "Overall_Average", avgScore)
            } else {
                println("Warning: Results are not numeric, cannot compute average.")
            }

            println("📈 Results logged to MLFlow for Run ID: $runId")
        }
    }

    abstract fun testCases(): List<TestCase<I>>

    abstract fun model(): Generator<I, O>

    abstract fun testFunctions(): List<EvaluationCriteria<O, R>>
}
