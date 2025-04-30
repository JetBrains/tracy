package ai.dev.kit.eval.utils.tracingDemo

import ai.dev.kit.core.eval.model.ModelData
import ai.dev.kit.core.fluent.FluentSpanAttributes
import ai.dev.kit.core.fluent.dataclasses.RunStatus
import ai.dev.kit.core.fluent.processor.TracingFlowProcessor
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Scope
import io.opentelemetry.extension.kotlin.asContextElement
import io.opentelemetry.sdk.trace.ReadableSpan
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.*
import java.util.stream.Stream


@TestInstance(org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS)
abstract class BaseEvaluationTest<
        AIInputT : ai.dev.kit.eval.utils.AIInput,
        GroundTruthT : ai.dev.kit.eval.utils.GroundTruth,
        AIOutputT : ai.dev.kit.eval.utils.AIOutput,
        EvalResultT : ai.dev.kit.eval.utils.EvalResult
        >(
    private val experimentName: String = "Evaluation test",
    private val runName: String? = null,
    private val numberOfRuns: Int = 1,
    private val tags: List<RunTag> = listOf(),
    private val evalClient: EvalClient
) {
    private lateinit var experimentId: String
    private var baselineText: String? = null
    private var modelData: ModelData? = null
    private val runResults =
        mutableListOf<RunResults<AIInputT, GroundTruthT, AIOutputT, EvalResultT>>()

    @BeforeAll
    fun beforeAll() {
//        println("🔄 Setting up before all tests")

        TracingFlowProcessor.setupTracing(evalClient.di)

//        if (tags.isNotEmpty()) Assertions.assertEquals(
//            tags.size,
//            numberOfRuns,
//            "The number of tags must match the number of runs"
//        )
//
//        experimentId = ai.dev.kit.providers.mlflow.getExperimentByName(
//            ai.dev.kit.providers.mlflow.KotlinMlflowClient,
//            experimentName
//        )?.experimentId
//            ?: ai.dev.kit.providers.mlflow.createExperiment(
//                ai.dev.kit.providers.mlflow.KotlinMlflowClient,
//                experimentName
//            )
//                    ?: throw IllegalStateException("Failed to create or retrieve experiment '$experimentName' at ${ai.dev.kit.providers.mlflow.KotlinMlflowClient.ML_FLOW_URL}")
//
//        ai.dev.kit.providers.mlflow.KotlinMlflowClient.currentExperimentId = experimentId
//        val baselineModelFile = getModelInfoFromFirstRun()
//
//        modelData = createModelData()
//
//        baselineText = baselineModelFile?.readText() ?: Json.encodeToString(modelData)
//
//        val runNameBase = runName ?: runBlocking { createRunName() }
//
        (1..numberOfRuns).map { runNum ->
//            runBlocking {
//                val runId = ai.dev.kit.providers.mlflow.createRun(
//                    ai.dev.kit.providers.mlflow.KotlinMlflowClient,
//                    runNameBase + if (numberOfRuns > 1) runNum else "",
//                    experimentId
//                )?.runId.toString()
//
//                ai.dev.kit.providers.mlflow.KotlinMlflowClient.currentRunId = runId
//
//                modelData?.runId = runId
//                setupMlflow(modelData, runId)
//
                runResults.add(RunResults(mutableListOf(), "runId", RunStatus.FINISHED))
//            }
        }
    }

//    private fun getModelInfoFromFirstRun(): File? {
//        val allRuns = ai.dev.kit.providers.mlflow.KotlinMlflowClient.listRunInfos(experimentId)
//        val firstRun = allRuns.lastOrNull() ?: return null
//
//        return try {
//            ai.dev.kit.providers.mlflow.KotlinMlflowClient.downloadArtifacts(firstRun.runId, "model/MLmodel")
//        } catch (e: Exception) {
//            println("Warning: Failed to download model info from the first run")
//            null
//        }
//    }

//    private suspend fun createRunName(): String {
//        val promptRules = """
//        Generate a concise and meaningful run name based on the following model parameters.
//
//        The rules are:
//        - Output exactly one run name with no explanations or additional text.
//        - The name must have at most five words.
//        - Use camel case or underscores if needed.
//    """
//        val modelDataJson = Json.encodeToString(modelData)
//
//        val prompt = buildString {
//            append(promptRules)
//            append("\n\nBaseline model parameters: $baselineText")
//            if (baselineText != modelDataJson) {
//                append("\nCurrent model parameters: $modelDataJson")
//                append("\nBase your name on diff between the Baseline and Current parameters")
//            }
//        }
//
//        val client = createAIClient()
//        return client.chatRequest(AIModel.GPT_4O_MINI, prompt, temperature = 1.0)
//    }

//    private fun createModelData(runId: String = "<PLACEHOLDER_RUN_ID>"): ModelData {
//        val metadata = generator.metadata
//
//        val modelData = ModelData(
//            runId = runId,
//            artifactPath = "model",
//            flavors = Flavors(
//                openai = OpenAI(
//                    openaiVersion = "1.60.2",
//                    data = "model.yaml",
//                    code = ""
//                )
//            ),
//            signature = Signature(
//                inputs = "[{\"type\": \"string\", \"required\": true}]",
//                outputs = "[{\"type\": \"string\", \"required\": true}]",
//                params = null
//            ),
//            modelParameters = ModelParameters(
//                prompt = metadata.prompt,
//                model = metadata.modelName,
//                temperature = metadata.temperature,
//            ),
//            utcTimeCreated = ai.dev.kit.providers.mlflow.getCurrentTimestamp().toString()
//        )
//
//        return modelData
//    }

//    private suspend fun setupMlflow(modelData: ModelData?, runId: String) {
//        println("🚀 MLFlow setup for run: $runId")
//        modelData ?: return
//
//        ai.dev.kit.providers.mlflow.logModel(runId, modelJson = createModelJson(modelData))
//
//        val loggedRun = ai.dev.kit.providers.mlflow.getRun(runId)
//        val modelArtifactPath = "${loggedRun.info.experimentId}/${runId}/artifacts/model/MLmodel"
//
//        ai.dev.kit.providers.mlflow.uploadArtifact(modelArtifactPath, createModelYaml(modelData))
//    }

    @AfterAll
    fun afterAll() {
        println("📊 Logging evaluation results")
//
//        runResults.forEachIndexed { runIndex, runResult ->
//            val (testResults, runId, runStatus) = runResult
//            try {
//                runBlocking {
//                    if (tags.isNotEmpty())
//                        ai.dev.kit.providers.mlflow.setTag(
//                            runId,
//                            "mlflow.runColor",
//                            tags[runIndex].color,
//                        )
//                }
//                uploadResultsTableAsArtifact(runId, testResults)
//                logAverageScore(runId, testResults.map { it.evalResult })
//            } catch (e: Exception) {
//                runResults[runIndex].finalStatus = RunStatus.FAILED
//            } finally {
//                runBlocking { ai.dev.kit.providers.mlflow.updateRun(runId, runStatus) }
//            }
//        }
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
        testCase: ai.dev.kit.eval.utils.TestCase<AIInputT, GroundTruthT>
    ): Pair<Span, Scope> {
        val tracedRunName = "Data Point ${dataPointIndex + 1}"
        val dataPointSpanBuilder = tracer.spanBuilder(tracedRunName).setNoParent()

        val dataPointSpan =
            runBlocking {
                dataPointSpanBuilder.setAttribute(
                    FluentSpanAttributes.SPAN_INPUTS.key,
                    "{\"Data Point\": \"${testCase.input}\"}"
                )

                val span = dataPointSpanBuilder.startSpan()
                evalClient.publishStartCall(
                    span as ReadableSpan, runId, tracedRunName
                )

                return@runBlocking span
            }
        val dataPointScope = dataPointSpan.makeCurrent()
        return dataPointSpan to dataPointScope
    }

    private fun executeSingleTest(
        testCaseName: String,
        testFunction: ai.dev.kit.eval.utils.Evaluator<GroundTruthT, AIOutputT, EvalResultT>,
        testCase: ai.dev.kit.eval.utils.TestCase<AIInputT, GroundTruthT>,
        runNum: Int,
        runId: String,
        output: AIOutputT
    ) {
        val result = try {
            testFunction.evaluate(testCase.groundTruth, output)
        } catch (e: Throwable) {
            val message = "❌ Test Failed: $testCaseName | Case: $testCase | Reason: Evaluator has thrown ${e.message}"
            logTest(message, runId)
            org.junit.jupiter.api.fail(message)
        }
//        runResults[runNum].testResults.add(
//            ai.dev.kit.providers.mlflow.dataclasses.TestResult<AIInputT, GroundTruthT, AIOutputT, EvalResultT>(
//                testCase,
//                output,
//                result
//            )
//        )

        if (result.hasJunitTestSucceeded) {
            logTest(
                message = "✅ Test Passed: $testCaseName | Case: $testCase | Result: $result",
                runId = runId
            )
        } else {
            val message = "❌ Test failed: $testCaseName | Case: $testCase | Result: $result"
            logTest(message, runId)
            org.junit.jupiter.api.fail(message)
        }
    }

    private fun logTest(message: String, runId: String) {
        println(message)
//        println("🔗 View results at ${ai.dev.kit.providers.mlflow.KotlinMlflowClient.ML_FLOW_URL}/#/experiments/$experimentId/runs/$runId")
    }

//    private fun uploadResultsTableAsArtifact(
//        runId: String,
//        testResults: List<ai.dev.kit.providers.mlflow.dataclasses.TestResult<AIInputT, GroundTruthT, AIOutputT, EvalResultT>>
//    ) {
//        val loggedRun = runBlocking { ai.dev.kit.providers.mlflow.getRun(runId) }
//        val artifactPath = "${loggedRun.info.experimentId}/${runId}/artifacts/eval_results_table.json"
//
//        val table = testResults.toTable() ?: return
//        ai.dev.kit.providers.mlflow.uploadArtifact(artifactPath, table.dumpForMLFlow())
//
//        runBlocking {
//            ai.dev.kit.providers.mlflow.setTag(
//                runId,
//                "mlflow.loggedArtifacts",
//                "[{\"path\": \"eval_results_table.json\", \"type\": \"table\"}]"
//            )
//        }
//    }

//    private fun logAverageScore(runId: String, evalResults: List<EvalResultT>) {
//        val aggregateScores = evaluator.aggregateResults(evalResults)
//        for (agg in aggregateScores) {
//            ai.dev.kit.providers.mlflow.logMetric(
//                ai.dev.kit.providers.mlflow.KotlinMlflowClient,
//                runId,
//                agg.scoreName,
//                agg.score
//            )
//        }
//        if (aggregateScores.isNotEmpty()) {
//            println("📈 Results logged to MLFlow for Run ID: $runId")
//        }
//    }

    abstract val testCases: List<ai.dev.kit.eval.utils.TestCase<AIInputT, GroundTruthT>>
    abstract val generator: ai.dev.kit.eval.utils.Generator<AIInputT, AIOutputT>
    abstract val evaluator: ai.dev.kit.eval.utils.Evaluator<GroundTruthT, AIOutputT, EvalResultT>
}