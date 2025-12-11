package ai.dev.kit.exporters.http

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import kotlin.test.assertIs

class LangfuseExportingTest {
    @Test
    fun `test custom OTLP span exporter gets created normally`() = runTest {
        val config = LangfuseExporterConfig()
        val exporter = assertDoesNotThrow { config.createSpanExporter() }
        assertIs<CustomOtlpHttpSpanExporter>(exporter)
    }
}