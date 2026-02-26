/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.core.instrumentation

import org.jetbrains.ai.tracy.core.TracingManager
import org.jetbrains.ai.tracy.test.utils.BaseOpenTelemetryTracingTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class DisableTracingTest() : BaseOpenTelemetryTracingTest() {
    @Test
    fun `test no spans are recorded when tracing is disabled`() = runTest {
        TracingManager.isTracingEnabled = false

        MyTestClass().testFunction(1)
        val traces = analyzeSpans()

        assertEquals(0, traces.size)
    }

    @Test
    fun `test spans are recorded when enabled and not recorded after disabling`() = runTest {
        MyTestClass().testFunction(1)
        val tracesEnabled = analyzeSpans()
        assertEquals(1, tracesEnabled.size)

        TracingManager.isTracingEnabled = false
        resetExporter()

        MyTestClass().testFunction(1)
        val tracesDisabled = analyzeSpans()

        assertEquals(0, tracesDisabled.size)
    }

    @Test
    fun `no spans are recorded for suspend functions when tracing is disabled`() = runTest {
        TracingManager.isTracingEnabled = false

        MyTestClassWithSuspend().testFunction(1)

        val traces = analyzeSpans()
        assertEquals(0, traces.size)
    }

    @Test
    fun `test spans are recorded for suspend when enabled and not recorded after disabling`() = runTest {
        MyTestClassWithSuspend().testFunction(1)
        val tracesEnabled = analyzeSpans()
        assertEquals(1, tracesEnabled.size)

        TracingManager.isTracingEnabled = false
        resetExporter()

        MyTestClassWithSuspend().testFunction(1)
        val tracesDisabled = analyzeSpans()

        assertEquals(0, tracesDisabled.size)
    }
}