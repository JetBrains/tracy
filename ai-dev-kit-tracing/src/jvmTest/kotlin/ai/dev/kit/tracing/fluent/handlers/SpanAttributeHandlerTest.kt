package ai.dev.kit.tracing.fluent.handlers

import ai.dev.kit.tracing.BaseOpenTelemetryTracingTest
import ai.dev.kit.tracing.fluent.KotlinFlowTrace
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class TestSpanAttributeHandlerClass {
    @KotlinFlowTrace
    fun baseAttributeHandler(param: Int): Int = param

    @KotlinFlowTrace(name = "Test Name")
    fun baseAttributeHandlerWithName(param: Int): Int = param

    @KotlinFlowTrace(attributeHandler = TestMetadataCustomizer::class)
    fun baseAttributeHandlerWithHandler(param: Int): Int = param

    @KotlinFlowTrace(name = "Test Name", attributeHandler = TestMetadataCustomizer::class)
    fun baseAttributeHandlerWithNameAndHandler(param: Int): Int = param

    object TestMetadataCustomizer : SpanMetadataCustomizer {
        override fun resolveSpanName(
            method: PlatformMethod,
            args: Array<Any?>,
            boundReceiverRuntimeClassName: String?
        ) = "Test.${method.name}"

        override fun formatInputAttributes(method: PlatformMethod, args: Array<Any?>): String =
            DefaultSpanMetadataCustomizer.formatInputAttributes(method, args)
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

    object BoundReceiverRuntimeClassNameAttributeHandler : SpanMetadataCustomizer {
        override fun resolveSpanName(
            method: PlatformMethod,
            args: Array<Any?>,
            boundReceiverRuntimeClassName: String?
        ): String? = boundReceiverRuntimeClassName

        override fun formatInputAttributes(
            method: PlatformMethod,
            args: Array<Any?>
        ): String = DefaultSpanMetadataCustomizer.formatInputAttributes(method, args)
    }

    object MethodDelegateClassNameAttributeHandler : SpanMetadataCustomizer {
        override fun resolveSpanName(
            method: PlatformMethod,
            args: Array<Any?>,
            boundReceiverRuntimeClassName: String?
        ): String? = method.declaringClass.name

        override fun formatInputAttributes(
            method: PlatformMethod,
            args: Array<Any?>
        ): String = DefaultSpanMetadataCustomizer.formatInputAttributes(method, args)
    }

    private abstract class TestClassWithBoundReceiverAndMethodDelegateBase {
        @KotlinFlowTrace(attributeHandler = BoundReceiverRuntimeClassNameAttributeHandler::class)
        fun foo() = 3

        @KotlinFlowTrace(attributeHandler = MethodDelegateClassNameAttributeHandler::class)
        fun boo() = 3
    }

    private class TestClassWithBoundReceiverAndMethodDelegateImpl :
        TestClassWithBoundReceiverAndMethodDelegateBase()

    @Test
    fun `span name comes from bound receiver runtime class when boundReceiverRuntimeClassName is used`() = runTest {
        TestClassWithBoundReceiverAndMethodDelegateImpl().foo()
        val traces = analyzeSpans()

        assertEquals(1, traces.size)
        val trace = traces.first()
        assertEquals(
            "ai.dev.kit.tracing.fluent.handlers.SpanAttributeHandlerTest\$TestClassWithBoundReceiverAndMethodDelegateImpl",
            trace.name
        )
    }

    @Test
    fun `span name falls back to default resolution when method declaring class name is used`() = runTest {
        TestClassWithBoundReceiverAndMethodDelegateImpl().boo()
        val traces = analyzeSpans()

        assertEquals(1, traces.size)
        val trace = traces.first()
        assertEquals(
            "ai.dev.kit.tracing.fluent.handlers.SpanAttributeHandlerTest\$TestClassWithBoundReceiverAndMethodDelegateBase",
            trace.name
        )
    }
}
