package ai.dev.kit.tracing.fluent

import ai.jetbrains.tracy.test.utils.BaseOpenTelemetryTracingTest
import ai.dev.kit.tracing.addLangfuseTagsToCurrentTrace
import io.opentelemetry.api.trace.SpanId
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.sdk.internal.ExceptionAttributeResolver
import io.opentelemetry.sdk.trace.data.ExceptionEventData
import io.opentelemetry.sdk.trace.data.StatusData
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class MyTestClass {
    @KotlinFlowTrace(name = "Main Span")
    fun testFunction(paramName: Int): Int {
        return paramName
    }

    @KotlinFlowTrace(name = "Main Span")
    fun testFunctionWithTag(paramName: Int): Int {
        addLangfuseTagsToCurrentTrace(listOf("Tag1", "Tag2"))
        return paramName
    }

    @KotlinFlowTrace(name = "Throws")
    fun testFunctionThrows(paramName: Int): Int {
        throw RuntimeException("Test exception")
    }

    @KotlinFlowTrace(name = "Main Span")
    fun testFunctionWithDefaultValue(paramName: Int = 10): Int {
        return paramName
    }

    @KotlinFlowTrace(name = "Secondary Span")
    fun anotherTestFunction(x: String): String {
        return x.reversed()
    }

    @KotlinFlowTrace(name = "Parent Span")
    fun parentTestFunction(x: String): String {
        return childTestFunction(x.reversed())
    }

    @KotlinFlowTrace(name = "Child Span")
    fun childTestFunction(x: String): String {
        return x.reversed()
    }

    internal class InsideClass() {
        @KotlinFlowTrace(name = "Inside Test Span")
        fun insideTestFunction(x: String): String {
            return x.reversed()
        }
    }

    @KotlinFlowTrace(name = "InlineOperation")
    inline fun <T> inlineOperation(name: String, block: () -> T): Pair<String, T> =
        name to block()

    @KotlinFlowTrace(name = "InlineOperation")
    inline fun <T> inlineOperationBlockFirst(block: () -> T, name: String): Pair<String, T> =
        name to block()

    @KotlinFlowTrace(name = "InlineOperation")
    inline fun <T> inlineOperationNoinlineBlock(name: String, noinline block: () -> T): Pair<String, T> =
        name to block()
}

internal class MyGenericTestClass<T> {
    @KotlinFlowTrace
    fun returnGenericParam(paramName: T): T {
        return paramName
    }

    @KotlinFlowTrace
    fun <V> returnTypeVWithTypeTParam(x: V, y: T): V {
        return x
    }
}

@KotlinFlowTrace(name = "Top Level Span")
internal fun topLevelTestFunction(x: String): String {
    return x.reversed()
}

@KotlinFlowTrace()
fun List<String>.foo(): String {
    return this.joinToString(" ")
}

@KotlinFlowTrace
fun <T> topLevelReturnGenericParam(paramName: T): T {
    return paramName
}

class FluentTracingTest() : BaseOpenTelemetryTracingTest() {
    @Test
    fun `test trace creation`() = runTest {
        MyTestClass().testFunction(1)
        val traces = analyzeSpans()

        assertEquals(1, traces.size)
        val trace = traces.firstOrNull()
        assertNotNull(trace)
    }

    @Test
    fun `test inline function`() = runTest {
        val input = "RandomString"
        MyTestClass().inlineOperation("name") {
            input.reversed()
        }
        val traces = analyzeSpans()

        assertEquals(1, traces.size)
        val trace = traces.firstOrNull()
        assertNotNull(trace)

        assertEquals(
            "{\"name\":\"name\",\"block\":\"null\"}",
            trace.getAttribute(FluentSpanAttributes.SPAN_INPUTS)
        )
        assertEquals(
            "(name, ${input.reversed()})",
            trace.getAttribute(FluentSpanAttributes.SPAN_OUTPUTS)
        )
    }

    @Test
    fun `test inline function block first`() = runTest {
        val input = "RandomString"
        MyTestClass().inlineOperationBlockFirst({
            input.reversed()
        }, "name")
        val traces = analyzeSpans()

        assertEquals(1, traces.size)
        val trace = traces.firstOrNull()
        assertNotNull(trace)

        assertEquals(
            "{\"block\":\"null\",\"name\":\"name\"}",
            trace.getAttribute(FluentSpanAttributes.SPAN_INPUTS),
        )
        assertEquals(
            "(name, ${input.reversed()})",
            trace.getAttribute(FluentSpanAttributes.SPAN_OUTPUTS)
        )
    }

    @Test
    fun `test inline function with noinline block`() = runTest {
        val input = "RandomString"
        MyTestClass().inlineOperationNoinlineBlock("name") {
            input.reversed()
        }
        val traces = analyzeSpans()

        assertEquals(1, traces.size)
        val trace = traces.firstOrNull()
        assertNotNull(trace)

        val inputs = trace.getAttribute(FluentSpanAttributes.SPAN_INPUTS)
        val outputs = trace.getAttribute(FluentSpanAttributes.SPAN_OUTPUTS)

        assertTrue(inputs!!.contains("\"name\":\"name\""))
        assertTrue(inputs.contains("\"block\":\"ai.dev.kit.tracing"))

        assertEquals(
            "(name, ${input.reversed()})",
            outputs
        )
    }

    @Test
    fun `test extension function`() = runTest {
        val result = listOf("first", "second").foo()

        val traces = analyzeSpans()
        assertEquals("first second", result)
        assertEquals(1, traces.size)
        val trace = traces.firstOrNull()
        assertNotNull(trace)
    }

    @Test
    fun `test top level function`() = runTest {
        topLevelTestFunction("RandomString")

        val traces = analyzeSpans()
        assertEquals(1, traces.size)
        val trace = traces.firstOrNull()
        assertNotNull(trace)
    }

    @Test
    fun `test inside class function`() = runTest {
        MyTestClass.InsideClass().insideTestFunction("RandomString")

        val traces = analyzeSpans()
        assertEquals(1, traces.size)
        val trace = traces.firstOrNull()
        assertNotNull(trace)
    }


    @Test
    fun `should trace return generic param in generic class`() = runTest {
        MyGenericTestClass<Int>().returnGenericParam(1)

        val traces = analyzeSpans()
        assertEquals(1, traces.size)
        val trace = traces.firstOrNull()
        assertNotNull(trace)
    }

    @Test
    fun `should trace return type V with type T param in generic class`() = runTest {
        MyGenericTestClass<Int>().returnTypeVWithTypeTParam("HI", 1)

        val traces = analyzeSpans()
        assertEquals(1, traces.size)
        val trace = traces.firstOrNull()
        assertNotNull(trace)
    }

    @Test
    fun `should trace top level return generic param function`() = runTest {
        topLevelReturnGenericParam(3)

        val traces = analyzeSpans()
        assertEquals(1, traces.size)
        val trace = traces.firstOrNull()
        assertNotNull(trace)
    }

    @Test
    fun `test trace tags and metadata are correct`() = runTest {
        val arg = 3
        val result = MyTestClass().testFunction(arg)

        val traces = analyzeSpans()

        assertEquals(1, traces.size)
        val trace = traces.firstOrNull()
        assertNotNull(trace)

        assertEquals(StatusData.ok(), trace.status)
        assertTrue(
            trace.getAttribute(FluentSpanAttributes.CODE_FUNCTION_NAME)?.endsWith("MyTestClass.testFunction") ?: false
        )
        assertEquals(
            trace.getAttribute(FluentSpanAttributes.SPAN_INPUTS),
            "{\"paramName\":3}"
        )
        assertEquals(
            trace.getAttribute(FluentSpanAttributes.SPAN_OUTPUTS),
            result.toString()
        )
    }


    @Test
    fun `test trace params default values are correct`() = runTest {
        val result = MyTestClass().testFunctionWithDefaultValue()

        val traces = analyzeSpans()
        assertEquals(1, traces.size)
        val trace = traces.firstOrNull()
        assertNotNull(trace)

        assertEquals(StatusData.ok(), trace.status)
        assertTrue(
            trace.getAttribute(FluentSpanAttributes.CODE_FUNCTION_NAME)?.endsWith("MyTestClass.testFunctionWithDefaultValue") ?: false
        )
        assertEquals(
            trace.getAttribute(FluentSpanAttributes.SPAN_INPUTS),
            "{\"paramName\":10}"
        )
        assertEquals(
            trace.getAttribute(FluentSpanAttributes.SPAN_OUTPUTS),
            result.toString()
        )
    }

    @Test
    fun `test multiple trace creation`() = runTest {
        val testClass = MyTestClass()
        testClass.testFunction(1)
        testClass.anotherTestFunction("OpenTelemetry")

        val traces = analyzeSpans()

        assertEquals(2, traces.size)
        assertNotEquals(
            traces.first(),
            traces.last(),
            message = "Trace IDs should be unique."
        )
    }

    @Test
    fun `test tag for current trace`() = runTest {
        val testClass = MyTestClass()
        testClass.testFunctionWithTag(1)

        val traces = analyzeSpans()

        assertEquals(1, traces.size)
        val trace = traces.firstOrNull()
        assertNotNull(trace)
        assertEquals(
            traces.first().getAttribute(FluentSpanAttributes.LANGFUSE_TRACE_TAGS),
            "[Tag1, Tag2]"
        )
    }

    @Test
    fun `test parent child trace`() = runTest {
        MyTestClass().parentTestFunction("RandomString")

        val traces = analyzeSpans()
        assertEquals(2, traces.size)
        val parentTrace = traces.find { it.parentSpanId == SpanId.getInvalid() }
        val childTrace = traces.find { it.parentSpanId != SpanId.getInvalid() }

        assertNotNull(parentTrace)
        assertNotNull(childTrace)

        assertEquals(StatusData.ok(), parentTrace.status)
        assertEquals(StatusData.ok(), childTrace.status)

        assertEquals(
            parentTrace.traceId,
            childTrace.traceId
        )
    }

    @Test
    fun `test status is error, when function throws`() = runTest {
        assertThrows<RuntimeException> {
            MyTestClass().testFunctionThrows(3)
        }

        val traces = analyzeSpans()
        assertEquals(1, traces.size)
        val trace = traces.firstOrNull()
        assertNotNull(trace)

        val exceptionEvent = trace.events.single { it is ExceptionEventData }
        assertEquals(StatusCode.ERROR, trace.status.statusCode)
        assertNotNull(exceptionEvent.attributes[ExceptionAttributeResolver.EXCEPTION_MESSAGE])
        assertNotNull(exceptionEvent.attributes[ExceptionAttributeResolver.EXCEPTION_STACKTRACE])
        assertNotNull(exceptionEvent.attributes[ExceptionAttributeResolver.EXCEPTION_TYPE])
    }
}
