package ai.features.haiku

import kotlinx.coroutines.runBlocking
import org.example.ai.AIModel
import org.example.ai.features.haiku.HaikuGenerator
import org.example.ai.mlflow.Metric
import org.example.ai.mlflow.RunStatus
import org.example.ai.mlflow.createRun
import org.example.ai.mlflow.getRun
import org.example.ai.mlflow.logBatch
import org.example.ai.mlflow.logModel
import org.example.ai.mlflow.logModelData
import org.example.ai.mlflow.setTag
import org.example.ai.mlflow.updateRun
import org.example.ai.model.Flavors
import org.example.ai.model.ModelData
import org.example.ai.model.ModelParameters
import org.example.ai.model.OpenAI
import org.example.ai.model.Signature
import org.example.ai.model.createModelJson
import org.junit.jupiter.api.extension.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.*
import kotlin.test.assertEquals


class HaikuGeneratorTest {
    companion object {
        private val testCases = listOf<String>("table", "computer", "horse")

        @JvmStatic
        fun provideTestCases(): List<Pair<String, Int>> {
            return testCases.map {
                it to 1
            }
        }

        fun getModel(): HaikuGenerator {
            return HaikuGenerator(AIModel.GPT_4O_MINI)
        }

        @SuppressWarnings("unused")
        @JvmField
        @RegisterExtension
        val evaluationLogger = EvaluationLogger(getModel())
    }

    /**
     * This test should be dynamically generated.
     */
    @ParameterizedTest
    @MethodSource("provideTestCases")
    fun `test consists of three lines`(testCase: Pair<String, Int>) {
        val (input, expected) = testCase

        // This in the framework code, so model logging could be implemented there.
        val model = getModel()

        val haiku = runBlocking { model.generateHaiku(input) }
        val result = ConsistsOfThreeLines.evaluate(haiku)

        assertEquals(expected, result, "The evaluation did not match for input: $input")
    }
}

/**
 * The user defines this.
 */
object ConsistsOfThreeLines : EvaluationCriteria<String, Int>("consists of three lines") {
    override fun evaluate(result: String): Int {
        return if (result.split("\n").size == 3) {
            1
        } else {
            0
        }
    }
}


/**
 * Part of the library. Logs test as evaluation runs to the tracking server.
 */
class EvaluationLogger(val model: HaikuGenerator) : TestWatcher, BeforeAllCallback, AfterAllCallback {
    private val EXPERIMENT_ID = "259381197825368132"
    // TODO: should be passed via ExtensionContext.Store
    private var runId: String? = null

    override fun beforeAll(context: ExtensionContext?) {
        context!!.getStore(ExtensionContext.Namespace.GLOBAL).put("evaluationLogger", mutableListOf<Double>())

        runBlocking {
            val run = createRun(context.displayName ?: "Test Run", EXPERIMENT_ID)
            runId = run.info.runId
            val id = runId!!

            val modelData = ModelData(
                runId = id,
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
                ),
                modelParameters = ModelParameters(
                    prompt = model.prompt,
                    model = model.model.name,
                    temperature = model.temperature
                )
            )

            logModel(id, modelJson = createModelJson(modelData))

            val loggedRun = getRun(id)
            val artifactUri = loggedRun.info.artifactUri

            logModelData(artifactUri, modelData)
        }
    }

    override fun testSuccessful(context: ExtensionContext) {
        val results = context.getStore(ExtensionContext.Namespace.GLOBAL).get("evaluationLogger") as MutableList<Double>
        results.add(1.0)
        println("✅ Test '${context.displayName}' PASSED.")
    }

    override fun testFailed(context: ExtensionContext, cause: Throwable?) {
        val results = context.getStore(ExtensionContext.Namespace.GLOBAL).get("evaluationLogger") as MutableList<Double>
        results.add(0.0)
        println("❌ Test '${context.displayName}' FAILED with error: ${cause?.message}")
    }

    override fun testDisabled(context: ExtensionContext, reason: Optional<String>) {
        println("⚠️ Test '${context.displayName}' SKIPPED. Reason: ${reason.orElse("No reason provided")}")
    }

    override fun testAborted(context: ExtensionContext, cause: Throwable?) {
        println("⏹️ Test '${context.displayName}' ABORTED.")
    }

    override fun afterAll(context: ExtensionContext?) {
        runBlocking {
            updateRun(runId!!, RunStatus.FINISHED)

            val results = context!!.getStore(ExtensionContext.Namespace.GLOBAL).get("evaluationLogger") as MutableList<Double>

            val average = results.average()
            logBatch(
                runId!!,
                listOf(
                    Metric(
                        "Consists of three lines",
                        average
                    )
                )
            )

            // TODO: store eval data here

            setTag(
                runId!!,
                "mlflow.loggedArtifacts",
                "[{\"path\": \"eval_results_table.json\", \"type\": \"table\"}]"
            )
        }
    }
}

/* API */

open class EvaluationTest<T, U>(val testCases: List<T>, val evaluationCriteria: EvaluationCriteria<T, U>) {
    fun evaluate(): List<U> {
        return testCases.map {
            evaluationCriteria.evaluate(it)
        }.toList()
    }
}

abstract class EvaluationCriteria<T, U>(val name: String) {
    abstract fun evaluate(result: T): U
}