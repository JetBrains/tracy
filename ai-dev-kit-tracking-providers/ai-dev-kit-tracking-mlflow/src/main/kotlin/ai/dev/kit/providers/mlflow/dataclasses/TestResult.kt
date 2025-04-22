package ai.dev.kit.providers.mlflow.dataclasses

import ai.dev.kit.core.eval.*

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

fun List<TestResult<*, *, *, *>>.toTable(): Table? {
    val indexColumn = Column(
        name = "#",
        data = (1..size).map { it.toString() }
    )

    val inputColumn = Column(
        name = "Input",
        data = map { it.testCase.input }
    )

    val outputColumn = Column(
        name = "Output",
        data = map { it.output }
    )

    val basicTable = tableOf(indexColumn, inputColumn, outputColumn)

    var evalResultsTable: Table? = map { it.evalResult }.toTable()
    if (evalResultsTable == null) {
        return basicTable
    }
    return basicTable join evalResultsTable
}
