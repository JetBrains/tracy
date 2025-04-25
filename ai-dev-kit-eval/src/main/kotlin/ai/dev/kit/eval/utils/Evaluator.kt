package ai.dev.kit.eval.utils

interface Evaluator<GroundTruthT: GroundTruth, AIOutputT: AIOutput, EvalResultT: EvalResult> {
    fun evaluate(groundTruth: GroundTruthT, output: AIOutputT): EvalResultT
    fun aggregateResults(results: List<EvalResultT>): List<AggregateScore> = emptyList()
    // TODO: fun evaluateCorpus(groundTruths: List<GroundTruthT>, outputs: List<AIOutputT>): List<CorpusEvalResult>
}
