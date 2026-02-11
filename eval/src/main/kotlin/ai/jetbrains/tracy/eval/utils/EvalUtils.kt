/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package ai.jetbrains.tracy.eval.utils

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
