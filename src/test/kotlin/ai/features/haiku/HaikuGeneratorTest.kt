package ai.features.haiku

import kotlinx.coroutines.runBlocking
import org.example.ai.features.haiku.generateHaiku
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
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
    }

    @ParameterizedTest
    @MethodSource("provideTestCases")
    fun `test consists of three lines`(testCase: Pair<String, Int>) {
        val (input, expected) = testCase

        val haiku = runBlocking { generateHaiku(input) }
        val result = ConsistsOfThreeLines.evaluate(haiku)

        assertEquals(expected, result, "The evaluation did not match for input: $input")
    }
}


object ConsistsOfThreeLines : EvaluationCriteria<String, Int>("consists of three lines") {
    override fun evaluate(result: String): Int {
        return if (result.split("\n").size == 3) {
            1
        } else {
            0
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