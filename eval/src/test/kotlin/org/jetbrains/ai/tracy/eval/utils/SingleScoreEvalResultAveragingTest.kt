/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.eval.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Test suite for the [averageSingleScoreEvalResults] function.
 *
 * The [averageSingleScoreEvalResults] function computes the average score from a list of [SingleScoreEvalResult].
 * It ensures that all results have the same score name, otherwise it returns `null`.
 */
class SingleScoreEvalResultAveragingTest {
    @Test
    fun `averageSingleScoreEvalResults returns 0_0 for empty list`() {
        val results = emptyList<SingleScoreEvalResult>()
        val average = averageSingleScoreEvalResults(results)
        assertEquals(0.0, average)
    }

    @Test
    fun `averageSingleScoreEvalResults computes average for single result`() {
        val results = listOf(
            SingleScoreEvalResult(scoreName = "accuracy", score = 0.9f)
        )

        val average = averageSingleScoreEvalResults(results)

        assertNotNull(average)
        assertEquals(0.9, average, absoluteTolerance = 0.001)
    }

    @Test
    fun `averageSingleScoreEvalResults computes average for multiple results with same scoreName`() {
        val results = listOf(
            SingleScoreEvalResult(scoreName = "accuracy", score = 0.8f),
            SingleScoreEvalResult(scoreName = "accuracy", score = 0.9f),
            SingleScoreEvalResult(scoreName = "accuracy", score = 1.0f)
        )

        val average = averageSingleScoreEvalResults(results)
        assertNotNull(average)
        assertEquals(0.9, average, absoluteTolerance = 0.001)
    }

    @Test
    fun `averageSingleScoreEvalResults returns null for results with different scoreNames`() {
        val results = listOf(
            SingleScoreEvalResult(scoreName = "accuracy", score = 0.8f),
            SingleScoreEvalResult(scoreName = "precision", score = 0.9f)
        )
        val average = averageSingleScoreEvalResults(results)

        assertNull(average)
    }
}