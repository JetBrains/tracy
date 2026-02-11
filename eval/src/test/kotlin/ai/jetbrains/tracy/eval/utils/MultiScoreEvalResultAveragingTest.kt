/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package ai.jetbrains.tracy.eval.utils

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for the [averageMultiScoreEvalResults] function.
 *
 * This function calculates the average scores for a list of [MultiScoreEvalResult]
 * objects, grouped by the score names.
 */
class MultiScoreEvalResultAveragingTest {
    @Test
    fun `test averageMultiScoreEvalResults with multiple results and matching score names`() {
        val results = listOf(
            MultiScoreEvalResult(
                listOf(
                    SingleScoreEvalResult("accuracy", 0.8f),
                    SingleScoreEvalResult("precision", 0.75f)
                )
            ),
            MultiScoreEvalResult(
                listOf(
                    SingleScoreEvalResult("accuracy", 0.9f),
                    SingleScoreEvalResult("precision", 0.85f)
                )
            )
        )

        val expected = mapOf(
            "accuracy" to 0.85,
            "precision" to 0.8
        )

        val actual = averageMultiScoreEvalResults(results)

        assertEquals(expected.size, actual.size)
        assertEquals(0.85, actual["accuracy"]!!, absoluteTolerance = 0.001)
        assertEquals(0.8, actual["precision"]!!, absoluteTolerance = 0.001)
    }

    @Test
    fun `test averageMultiScoreEvalResults with empty input`() {
        val results = emptyList<MultiScoreEvalResult>()

        val expected = emptyMap<String, Double>()

        val actual = averageMultiScoreEvalResults(results)

        assertEquals(expected, actual)
    }

    @Test
    fun `test averageMultiScoreEvalResults with varying score names`() {
        val results = listOf(
            MultiScoreEvalResult(
                listOf(
                    SingleScoreEvalResult("accuracy", 0.6f),
                    SingleScoreEvalResult("recall", 0.7f)
                )
            ),
            MultiScoreEvalResult(
                listOf(
                    SingleScoreEvalResult("accuracy", 0.8f)
                )
            )
        )

        val actual = averageMultiScoreEvalResults(results)
        assertEquals(actual.size, 1)
        assertEquals(0.7, actual["accuracy"]!!, absoluteTolerance = 0.001)
    }

    @Test
    fun `test averageMultiScoreEvalResults with consistent single score`() {
        val results = listOf(
            MultiScoreEvalResult(
                listOf(
                    SingleScoreEvalResult("accuracy", 0.5f)
                )
            ),
            MultiScoreEvalResult(
                listOf(
                    SingleScoreEvalResult("accuracy", 0.9f)
                )
            )
        )
        val actual = averageMultiScoreEvalResults(results)
        assertEquals(1, actual.size)
        assertEquals(0.7, actual["accuracy"]!!, absoluteTolerance = 0.001)
    }

    @Test
    fun `test averageMultiScoreEvalResults with inconsistent scores`() {
        val results = listOf(
            MultiScoreEvalResult(
                listOf(
                    SingleScoreEvalResult("accuracy", 0.6f),
                    SingleScoreEvalResult("precision", 0.8f)
                )
            ),
            MultiScoreEvalResult(
                listOf(
                    SingleScoreEvalResult("recall", 0.9f)
                )
            )
        )

        val expected = emptyMap<String, Double>() // No valid averages because scores are inconsistent.
        val actual = averageMultiScoreEvalResults(results)
        assertEquals(expected, actual)
    }

    @Test
    fun `test averageMultiScoreEvalResults with single result`() {
        val results = listOf(
            MultiScoreEvalResult(
                listOf(
                    SingleScoreEvalResult("accuracy", 0.95f)
                )
            )
        )

        val actual = averageMultiScoreEvalResults(results)
        assertEquals(1, actual.size)
        assertEquals(0.95, actual["accuracy"]!!, absoluteTolerance = 0.001)
    }

    @Test
    fun `test averageMultiScoreEvalResults with all scores equal`() {
        val results = listOf(
            MultiScoreEvalResult(
                listOf(
                    SingleScoreEvalResult("accuracy", 1.0f),
                    SingleScoreEvalResult("precision", 1.0f)
                )
            ),
            MultiScoreEvalResult(
                listOf(
                    SingleScoreEvalResult("accuracy", 1.0f),
                    SingleScoreEvalResult("precision", 1.0f)
                )
            )
        )

        val expected = mapOf(
            "accuracy" to 1.0,
            "precision" to 1.0
        )

        val actual = averageMultiScoreEvalResults(results)

        assertEquals(expected, actual)
    }
}