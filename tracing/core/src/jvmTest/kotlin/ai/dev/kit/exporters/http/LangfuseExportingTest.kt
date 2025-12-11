package ai.dev.kit.exporters.http

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
        assertIs<CustomOtlpHttpSpanExporter>(exporter)
    }
}