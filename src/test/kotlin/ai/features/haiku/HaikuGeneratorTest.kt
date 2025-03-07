package ai.features.haiku

import org.example.ai.AIModel
import org.example.ai.features.haiku.HaikuGenerator
import org.example.ai.mlflow.BaseEvaluationTest
import org.example.ai.mlflow.dataclasses.EvaluationCriteria
import org.example.ai.mlflow.dataclasses.RunTag
import org.example.ai.mlflow.dataclasses.TestCase
import org.example.ai.mlflow.fluent.KotlinFlowTrace


class HaikuGeneratorTest :
    BaseEvaluationTest<String, String, Double>(
        "HaikuGeneratorTest",
        numberOfRuns = 1,
        tags = listOf(RunTag(color = "#FF0000")),
    ) {
    override fun testCases(): List<TestCase<String, Double>> {
        return listOf(
            TestCase("table", 1.0),
            TestCase("computer", 1.0),
            TestCase("flower", 1.0),
            TestCase("horse", 1.0)
        )
    }

    override fun model(): HaikuGenerator {
        return HaikuGenerator(AIModel.GPT_4O_MINI)
    }

    override fun testFunctions(): List<EvaluationCriteria<String, Double>> {
        return listOf(
            ConsistsOfThreeLines,
//            ContainsTheWordIs,
        )
    }

    override fun getMLflowRunsDir() = "/Users/Anton.Bragin/PycharmProjects/litellm-test/mlruns"
}

/**
 * Test to evaluate haiku line structure.
 *
 * In English tradition haiku should consist of three lines.
 */
object ConsistsOfThreeLines : EvaluationCriteria<String, Double>("consists of three lines") {
    @KotlinFlowTrace(name = "Three Lines")
    override fun evaluate(result: String): Double {
        return if (result.split("\n").size == 3) {
            1.0
        } else {
            0.0
        }
    }
}

object ContainsTheWordIs : EvaluationCriteria<String, Double>("contains the word is") {
    override fun evaluate(result: String): Double {
        return if (result.split(" ").map { it.trim().lowercase() }.contains("is")) 1.0 else 0.0
    }
}