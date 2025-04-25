package ai.dev.kit.eval.utils

/**
 * The input of the AI feature under test.
 */
interface AIInput

/**
 * The output of the AI feature under test
 */
interface AIOutput

/**
 * Extra information that can help assess a particular [AIOutput].
 * For example, the expected ground-truth answer, or
 * additional instructions for the LLM-as-a-Judge evaluator.
 */
interface GroundTruth

/**
 * If there's nothing resembling a ground truth in your setting
 * and the evaluation should be based solely on [AIOutput],
 * use the [NoGroundTruth] stub.
 */
data object NoGroundTruth : GroundTruth

data class TestCase<AIInputT : AIInput, GroundTruthT : GroundTruth>(
    val name: String,
    val input: AIInputT,
    val groundTruth: GroundTruthT
)