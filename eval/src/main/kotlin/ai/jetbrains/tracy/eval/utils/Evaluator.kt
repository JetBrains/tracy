/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package ai.jetbrains.tracy.eval.utils

interface Evaluator<GroundTruthT : GroundTruth, AIOutputT : AIOutput, EvalResultT : EvalResult> {
    fun evaluate(groundTruth: GroundTruthT, output: AIOutputT): EvalResultT
    fun aggregateResults(results: List<EvalResultT>): List<AggregateScore> = emptyList()
    // TODO: fun evaluateCorpus(groundTruths: List<GroundTruthT>, outputs: List<AIOutputT>): List<CorpusEvalResult>
}
