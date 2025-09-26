package ai.dev.kit.eval.utils

import ai.dev.kit.eval.providers.dataclasses.RunStatus

data class RunTag(
    val color: String
)

internal data class RunResults<
        AIInputT : AIInput,
        GroundTruthT : GroundTruth,
        AIOutputT : AIOutput,
        EvalResultT : EvalResult
        >(
    val testResults: MutableList<TestResult<AIInputT, GroundTruthT, AIOutputT, EvalResultT>>,
    val runId: String,
    var finalStatus: RunStatus,
)
