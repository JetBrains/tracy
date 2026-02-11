/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package ai.jetbrains.tracy.eval.utils

import org.jetbrains.kotlinx.dataframe.api.cast
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test


/**
 * Test suite for the [toTable] function in the [EvalResult] class.
 *
 * The [toTable] function converts a list of [EvalResult] into a `DataFrame<Float>`.
 * It handles both [SingleScoreEvalResult] and [MultiScoreEvalResult] objects.
 */
class EvalResultToDataFrameTest {
    @Test
    fun `toTable should return null for an empty list`() {
        val evalResults = emptyList<EvalResult>()
        val result = evalResults.toTable()
        assertNull(result, "Expected null for an empty input list")
    }

    @Test
    fun `toTable should return DataFrame for SingleScoreEvalResults with consistent score names`() {
        val evalResults = listOf(
            SingleScoreEvalResult("accuracy", 0.8f),
            SingleScoreEvalResult("accuracy", 0.9f),
            SingleScoreEvalResult("accuracy", 0.85f)
        )
        val result = evalResults.toTable()
        assertNotNull(result, "Expected a DataFrame for consistent SingleScoreEvalResults")
        assertEquals(3, result!!.rowsCount(), "Expected one column in the DataFrame")
        assertEquals(1, result.columnsCount(), "Expected three rows in the DataFrame")
        assertEquals("accuracy", result.columnNames()[0], "Expected column name to be 'accuracy'")
        assertTrue(result["accuracy"].cast<Float>().toList() == listOf(0.8f, 0.9f, 0.85f))
    }

    @Test
    fun `toTable should return null for SingleScoreEvalResults with inconsistent score names`() {
        val evalResults = listOf(
            SingleScoreEvalResult("accuracy", 0.8f),
            SingleScoreEvalResult("precision", 0.9f),
            SingleScoreEvalResult("recall", 0.85f)
        )
        val result = evalResults.toTable()
        assertNull(result, "Expected null due to inconsistent score names")
    }

    @Test
    fun `toTable should return DataFrame for MultiScoreEvalResults with consistent score names`() {
        val evalResults = listOf(
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
        val result = evalResults.toTable()
        assertNotNull(result, "Expected a DataFrame for consistent MultiScoreEvalResults")
        assertEquals(2, result!!.columnsCount(), "Expected two columns in the DataFrame")
        assertEquals(2, result.rowsCount(), "Expected two rows in the DataFrame")
        assertEquals(setOf("accuracy", "precision"), result.columnNames().toSet(), "Expected correct column names")
        assertTrue(result["accuracy"].cast<Float>().toList() == listOf(0.8f, 0.9f))
        assertTrue(result["precision"].cast<Float>().toList() == listOf(0.75f, 0.85f))
    }

    @Test
    fun `toTable should return null for MultiScoreEvalResults with missing scores`() {
        val evalResults = listOf(
            MultiScoreEvalResult(
                listOf(
                    SingleScoreEvalResult("accuracy", 0.8f),
                    SingleScoreEvalResult("precision", 0.75f)
                )
            ),
            MultiScoreEvalResult(
                listOf(
                    SingleScoreEvalResult("accuracy", 0.9f)
                )
            )
        )
        val result = evalResults.toTable()
        assertNull(result, "Expected null due to missing scores in MultiScoreEvalResults")
    }

    @Test
    fun `toTable should return null for heterogeneous list of EvalResults`() {
        val evalResults = listOf(
            SingleScoreEvalResult("accuracy", 0.8f),
            MultiScoreEvalResult(
                listOf(
                    SingleScoreEvalResult("accuracy", 0.9f),
                    SingleScoreEvalResult("precision", 0.85f)
                )
            )
        )
        val result = evalResults.toTable()
        assertNull(result, "Expected null for heterogeneous list of EvalResults")
    }
}