package ai.dev.kit.providers.langfuse.tracingDemo

import ai.dev.kit.eval.utils.tracingDemo.BaseEvaluationTest
import ai.dev.kit.eval.utils.tracingDemo.RunTag

abstract class LangfuseEvaluationTest<
        AIInputT : ai.dev.kit.eval.utils.AIInput,
        GroundTruthT : ai.dev.kit.eval.utils.GroundTruth,
        AIOutputT : ai.dev.kit.eval.utils.AIOutput,
        EvalResultT : ai.dev.kit.eval.utils.EvalResult
        >
    (
    experimentName: String = "Evaluation test",
    runName: String? = null,
    numberOfRuns: Int = 1,
    tags: List<RunTag> = listOf()
) : BaseEvaluationTest<AIInputT, GroundTruthT, AIOutputT, EvalResultT>(
    experimentName,
    runName,
    numberOfRuns,
    tags,
    LangfuseEvalClient()
)