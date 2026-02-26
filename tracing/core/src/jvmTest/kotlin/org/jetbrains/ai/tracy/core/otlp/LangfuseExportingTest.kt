/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.core.otlp

import org.jetbrains.ai.tracy.core.exporters.langfuse.LangfuseExporterConfig
import org.jetbrains.ai.tracy.core.exporters.otlp.ErrorDiagnosingOtlpHttpSpanExporter
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.test.assertIs

class LangfuseExportingTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "LANGFUSE_PUBLIC_KEY", matches = ".+")
    @EnabledIfEnvironmentVariable(named = "LANGFUSE_SECRET_KEY", matches = ".+")
    fun `test custom OTLP span exporter gets created normally`() = runTest {
        val config = LangfuseExporterConfig()
        val exporter = assertDoesNotThrow { config.createSpanExporter() }
        assertIs<ErrorDiagnosingOtlpHttpSpanExporter>(exporter)
    }
}