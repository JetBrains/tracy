package ai.dev.kit.tracing.fluent.handlers

import ai.dev.kit.tracing.BaseOpenTelemetryTracingTest
import ai.dev.kit.tracing.fluent.KotlinFlowTrace
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

private class TestSpanAttributeHandlerClass {
    @KotlinFlowTrace
    fun baseAttributeHandler(param: Int): Int = param

    @KotlinFlowTrace(name = "Test Name")
    fun baseAttributeHandlerWithName(param: Int): Int = param

    @KotlinFlowTrace(metadataCustomizer = TestMetadataCustomizer::class)
    fun baseAttributeHandlerWithHandler(param: Int): Int = param

    @KotlinFlowTrace(name = "Test Name", metadataCustomizer = TestMetadataCustomizer::class)
    fun baseAttributeHandlerWithNameAndHandler(param: Int): Int = param

    object TestMetadataCustomizer : SpanMetadataCustomizer {
        override fun formatInputAttributes(method: PlatformMethod, args: Array<Any?>): String =
            DefaultSpanMetadataCustomizer.formatInputAttributes(method, args)

        override fun resolveSpanName(method: PlatformMethod, args: Array<Any?>): String =
            "Test.${method.name}"
    }
}

class SpanAttributeHandlerTest : BaseOpenTelemetryTracingTest() {
    @Test
    fun `test span name defaults to method name`() = runTest {
        TestSpanAttributeHandlerClass().baseAttributeHandler(1)
        val traces = analyzeSpans()

        assertEquals(1, traces.size)
        val trace = traces.first()
        assertEquals("baseAttributeHandler", trace.name)
    }

    @Test
    fun `test resolve span name from customizer`() = runTest {
        TestSpanAttributeHandlerClass().baseAttributeHandlerWithName(1)
        val traces = analyzeSpans()

        assertEquals(1, traces.size)
        val trace = traces.first()
        assertEquals("Test Name", trace.name)
    }

    @Test
    fun `test span name from attribute customizer`() = runTest {
        TestSpanAttributeHandlerClass().baseAttributeHandlerWithHandler(1)
        val traces = analyzeSpans()

        assertEquals(1, traces.size)
        val trace = traces.first()
        assertEquals(
            "Test.baseAttributeHandlerWithHandler",
            trace.name
        )
    }

    @Test
    fun `test span name customizer overrides annotation`() = runTest {
        TestSpanAttributeHandlerClass().baseAttributeHandlerWithNameAndHandler(1)
        val traces = analyzeSpans()

        assertEquals(1, traces.size)
        val trace = traces.first()
        assertEquals(
            "Test.baseAttributeHandlerWithNameAndHandler",
            trace.name
        )
    }
}
