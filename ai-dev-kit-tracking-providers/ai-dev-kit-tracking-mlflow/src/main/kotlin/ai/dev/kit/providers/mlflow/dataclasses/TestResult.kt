package ai.dev.kit.providers.mlflow.dataclasses

import ai.dev.kit.eval.utils.*
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.add
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf


data class TestResult<
        AIInputT : AIInput,
        GroundTruthT : GroundTruth,
        AIOutputT : AIOutput,
        EvalResultT : EvalResult
        >(
    val testCase: TestCase<AIInputT, GroundTruthT>,
    val output: AIOutputT,
    val evalResult: EvalResultT
)

fun List<TestResult<*, *, *, *>>.toTable(): DataFrame<*>? {
    val basicTable = dataFrameOf(
        "#" to (1..size).map { it.toString() },
        "Input" to map { it.testCase.input },
        "Output" to map { it.output },
    )

    var evalResultsTable: DataFrame<Float>? = map { it.evalResult }.toTable()
    if (evalResultsTable == null) {
        return basicTable
    }
    return basicTable.add(evalResultsTable)
}
