package ai.dev.kit.tracing.fluent

import io.opentelemetry.sdk.trace.data.StatusData
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tracing.ai.dev.kit.tracing.BaseOpenTelemetryTracingTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


class KotlinFlowTraceInheritanceTest : BaseOpenTelemetryTracingTest() {
    private abstract class TestClassPropagationBase {
        @KotlinFlowTrace
        abstract suspend fun withPropagation(param: Int): Int
    }

    private class TestClassPropagationImpl : TestClassPropagationBase() {
        override suspend fun withPropagation(param: Int): Int = 42 + param
    }

    @Test
    fun `base annotated with propagation, override traced`() = runTest {
        val result = TestClassPropagationImpl().withPropagation(1)
        val traces = analyzeSpans()

        assertEquals(1, traces.size)
        val trace = traces.first()
        assertEquals(
            "withPropagation",
            trace.getAttribute(FluentSpanAttributes.SPAN_FUNCTION_NAME)
        )
        assertEquals(StatusData.ok(), trace.status)
        assertEquals(
            trace.getAttribute(FluentSpanAttributes.SPAN_INPUTS),
            "{\"param\":1}"
        )
        assertEquals(
            trace.getAttribute(FluentSpanAttributes.SPAN_OUTPUTS),
            result.toString()
        )
    }

    private interface TestClassPropagationInterface {
        @KotlinFlowTrace
        suspend fun fromInterface(param: Int): Int
    }

    private class TestClassPropagationInterfaceImpl : TestClassPropagationInterface {
        override suspend fun fromInterface(param: Int): Int = 42 + param
    }

    @Test
    fun `interface annotated with propagation, impl traced`() = runTest {
        val result = TestClassPropagationInterfaceImpl().fromInterface(1)
        val traces = analyzeSpans()

        assertEquals(1, traces.size)
        val trace = traces.first()
        assertEquals(
            "fromInterface",
            trace.getAttribute(FluentSpanAttributes.SPAN_FUNCTION_NAME)
        )
        assertEquals(
            trace.getAttribute(FluentSpanAttributes.SPAN_INPUTS),
            "{\"param\":1}"
        )
        assertEquals(
            trace.getAttribute(FluentSpanAttributes.SPAN_OUTPUTS),
            result.toString()
        )
    }

    private class TestClassDirectlyAnnotated {
        @KotlinFlowTrace
        fun directlyAnnotated(param: Int): Int = 42 + param
    }

    @Test
    fun `directly annotated method, traced`() = runTest {
        val result = TestClassDirectlyAnnotated().directlyAnnotated(1)
        val traces = analyzeSpans()

        assertEquals(1, traces.size)
        val trace = traces.first()
        assertEquals(
            "directlyAnnotated",
            trace.getAttribute(FluentSpanAttributes.SPAN_FUNCTION_NAME)
        )
        assertEquals(
            trace.getAttribute(FluentSpanAttributes.SPAN_INPUTS),
            "{\"param\":1}"
        )
        assertEquals(
            trace.getAttribute(FluentSpanAttributes.SPAN_OUTPUTS),
            result.toString()
        )
    }

    private class TestClassNoAnnotation {
        fun plainMethod(param: Int): Int = 42 + param
    }

    @Test
    fun `no annotation at all, not traced`() = runTest {
        TestClassNoAnnotation().plainMethod(5)
        val traces = analyzeSpans()
        assertTrue { traces.isEmpty() }
    }

    private interface TestClassDeepTraceableInterface {
        @KotlinFlowTrace
        suspend fun deepMethod(param: Int): Int
    }

    private abstract class TestClassDeepAbstractClass : TestClassDeepTraceableInterface

    private open class TestClassDeepImpl : TestClassDeepAbstractClass() {
        override suspend fun deepMethod(param: Int): Int = 42 + param
    }

    @Test
    fun `3-level hierarchy with interface propagation, final impl traced`() = runTest {
        val result = TestClassDeepImpl().deepMethod(1)
        val traces = analyzeSpans()

        assertEquals(1, traces.size)
        val trace = traces.first()
        assertNotNull(
            trace,
            "Final implementation should still be traced due to interface propagation"
        )
        assertEquals(
            "deepMethod",
            trace.getAttribute(FluentSpanAttributes.SPAN_FUNCTION_NAME)
        )
        assertEquals(
            trace.getAttribute(FluentSpanAttributes.SPAN_INPUTS),
            "{\"param\":1}"
        )
        assertEquals(
            trace.getAttribute(FluentSpanAttributes.SPAN_OUTPUTS),
            result.toString()
        )
    }

    private class TestClassDeeperDeepImpl : TestClassDeepImpl() {
        override suspend fun deepMethod(param: Int): Int = 42
    }

    @Test
    fun `4-level hierarchy with interface propagation, final impl traced`() = runTest {
        val result = TestClassDeeperDeepImpl().deepMethod(1)
        val traces = analyzeSpans()

        assertEquals(1, traces.size)
        val trace = traces.first()
        assertNotNull(
            trace,
            "Final implementation should still be traced due to interface propagation"
        )
        assertEquals(
            "deepMethod",
            trace.getAttribute(FluentSpanAttributes.SPAN_FUNCTION_NAME)
        )
        assertEquals(
            trace.getAttribute(FluentSpanAttributes.SPAN_INPUTS),
            "{\"param\":1}"
        )
        assertEquals(
            trace.getAttribute(FluentSpanAttributes.SPAN_OUTPUTS),
            result.toString()
        )
    }
}

