package ai.features.haiku

import kotlinx.coroutines.runBlocking
import org.example.ai.AIModel
import org.example.ai.features.haiku.HaikuGenerator
import org.junit.jupiter.api.extension.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.assertEquals


class HaikuGeneratorTest {
    /**
     * This test will be generated dynamically.
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

    companion object {
        private val testCases = listOf<String>("table", "computer", "horse", "flower")

        @JvmStatic
        fun provideTestCases(): List<Pair<String, Int>> {
            return testCases.map {
                it to 1
            }
        }

        fun getModel(): HaikuGenerator {
            return HaikuGenerator(AIModel.GPT_4O_MINI)
        }

        @JvmField
        @RegisterExtension
        @Suppress("unused")
        val evaluationLogger = EvaluationLogger(getModel())
    }
}

/**
 * Test to evaluate haiku line structure.
 *
 * In English tradition haiku should consist of three lines.
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