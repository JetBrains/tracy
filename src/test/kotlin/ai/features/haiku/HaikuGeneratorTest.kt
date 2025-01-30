package ai.features.haiku

import kotlinx.coroutines.runBlocking
import org.example.ai.features.haiku.generateHaiku
import org.example.ai.mlflow.createRun
import org.junit.jupiter.api.extension.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.*
import kotlin.test.assertEquals

@ExtendWith(EvaluationLogger::class)
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


/**
 * Part of the library. Logs test as evaluation runs to the tracking server.
 */
class EvaluationLogger : TestWatcher, BeforeAllCallback, AfterAllCallback {
    override fun beforeAll(context: ExtensionContext?) {
        runBlocking { createRun("My First Run") }
    }

    override fun testSuccessful(context: ExtensionContext) {
        println("✅ Test '${context.displayName}' PASSED.")
    }

    override fun testFailed(context: ExtensionContext, cause: Throwable?) {
        println("❌ Test '${context.displayName}' FAILED with error: ${cause?.message}")
    }

    override fun testDisabled(context: ExtensionContext, reason: Optional<String>) {
        println("⚠️ Test '${context.displayName}' SKIPPED. Reason: ${reason.orElse("No reason provided")}")
    }

    override fun testAborted(context: ExtensionContext, cause: Throwable?) {
        println("⏹️ Test '${context.displayName}' ABORTED.")
    }

    override fun afterAll(context: ExtensionContext?) {
        println("EVALUATION FINISHED!")
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