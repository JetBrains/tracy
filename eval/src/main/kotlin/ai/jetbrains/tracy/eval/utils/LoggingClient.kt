/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package ai.jetbrains.tracy.eval.utils


interface LoggingClient {
    val clientName: String
    fun createRun(experimentId: String, runName: String): String
    fun getTraceLink(experimentId: String, traceId: String): String
    fun getRunLink(experimentId: String, runId: String): String
    suspend fun getOrCreateExperiment(experimentName: String): String?
    suspend fun logMetric(runId: String, name: String, score: Double, traceId: String? = null)
    suspend fun uploadResults(runId: String, testResults: List<TestResult<*, *, *, *>>)
}
