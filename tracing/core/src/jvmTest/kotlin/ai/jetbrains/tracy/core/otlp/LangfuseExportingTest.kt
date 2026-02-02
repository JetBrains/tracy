package ai.jetbrains.tracy.core.otlp

import ai.jetbrains.tracy.core.exporters.otlp.ErrorDiagnosingOtlpHttpSpanExporter
import ai.jetbrains.tracy.core.exporters.langfuse.LangfuseExporterConfig
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