package ai.dev.kit.tracing.fluent

import io.opentelemetry.api.trace.SpanId
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.data.StatusData
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class MyTestClass {
    @KotlinFlowTrace(name = "Main Span", spanType = "mySpanType")
    fun testFunction(paramName: Int): Int {
        return paramName
    }

    @KotlinFlowTrace(name = "Throws")
    fun testFunctionThrows(paramName: Int): Int {
        throw RuntimeException("Test exception")
        return paramName
    }

    @KotlinFlowTrace(name = "Main Span", spanType = "mySpanType")
    fun testFunctionWithDefaultValue(paramName: Int = 10): Int {
        return paramName
    }

    @KotlinFlowTrace(name = "Secondary Span", spanType = "func")
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

class FluentTracingTest() : BaseTracingTest() {
    @Test
    fun `test trace creation`() = runTest {
        val experimentId = createExperimentId()
        withProjectId(experimentId) {
            MyTestClass().testFunction(1)
        }

        val traces = getTraces(experimentId)

        assertEquals(1, traces.size)
        val trace = traces.firstOrNull()
        assertNotNull(trace)
    }

    @Test
    fun `test extension function`() = runTest {
        val experimentId = createExperimentId()
        val result = withProjectId(experimentId) {
            listOf("first", "second").foo()
        }

        val traces = getTraces(experimentId)

        assertEquals("first second", result)
        assertEquals(1, traces.size)
        val trace = traces.firstOrNull()
        assertNotNull(trace)
    }

    @Test
    fun `test top level function`() = runTest {
        val experimentId = createExperimentId()
        withProjectId(experimentId) {
            topLevelTestFunction("RandomString")
        }

        val traces = getTraces(experimentId)

        assertEquals(1, traces.size)
        val trace = traces.firstOrNull()
        assertNotNull(trace)
    }

    @Test
    fun `test inside class function`() = runTest {
        val experimentId = createExperimentId()
        withProjectId(experimentId) {
            MyTestClass.InsideClass().insideTestFunction("RandomString")
        }

        val traces = getTraces(experimentId)

        assertEquals(1, traces.size)
        val trace = traces.firstOrNull()
        assertNotNull(trace)
    }


    @Test
    fun `should trace return generic param in generic class`() = runTest {
        val experimentId = createExperimentId()
        withProjectId(experimentId) {
            MyGenericTestClass<Int>().returnGenericParam(1)
        }

        val traces = getTraces(experimentId)

        assertEquals(1, traces.size)
        val trace = traces.firstOrNull()
        assertNotNull(trace)
    }

    @Test
    fun `should trace return type V with type T param in generic class`() = runTest {
        val experimentId = createExperimentId()
        withProjectId(experimentId) {
            MyGenericTestClass<Int>().returnTypeVWithTypeTParam("HI", 1)
        }

        val traces = getTraces(experimentId)

        assertEquals(1, traces.size)
        val trace = traces.firstOrNull()
        assertNotNull(trace)
    }

    @Test
    fun `should trace top level return generic param function`() = runTest {
        val experimentId = createExperimentId()
        withProjectId(experimentId) {
            topLevelReturnGenericParam(3)
        }

        val traces = getTraces(experimentId)

        assertEquals(1, traces.size)
        val trace = traces.firstOrNull()
        assertNotNull(trace)
    }

    @Test
    fun `test trace tags and metadata are correct`() = runTest {
        val experimentId = createExperimentId()
        val arg = 3
        val result = withProjectId(experimentId) {
            MyTestClass().testFunction(arg)
        }

        val traces = getTraces(experimentId)

        assertEquals(1, traces.size)
        val trace = traces.firstOrNull() as? SpanData
        assertNotNull(trace)

        assertEquals(StatusData.ok(), trace.status)
        assertEquals(
            "testFunction",
            trace.getAttribute(FluentSpanAttributes.SPAN_FUNCTION_NAME)
        )
        assertTrue(
            trace.getAttribute(FluentSpanAttributes.SPAN_SOURCE_NAME)?.endsWith("MyTestClass") ?: false
        )
        assertEquals(
            trace.getAttribute(FluentSpanAttributes.SPAN_INPUTS),
            "{\"paramName\":3}"
        )
        assertEquals(
            trace.getAttribute(FluentSpanAttributes.SPAN_OUTPUTS),
            result.toString()
        )
        assertEquals(
            "mySpanType",
            trace.getAttribute(FluentSpanAttributes.SPAN_TYPE)
        )
    }


    @Test
    fun `test trace params default values are correct`() = runTest {
        val experimentId = createExperimentId()
        val result = withProjectId(experimentId) {
            MyTestClass().testFunctionWithDefaultValue()
        }

        val traces = getTraces(experimentId)

        assertEquals(1, traces.size)
        val trace = traces.firstOrNull() as? SpanData
        assertNotNull(trace)

        assertEquals(StatusData.ok(), trace.status)
        assertEquals(
            "testFunctionWithDefaultValue",
            trace.getAttribute(FluentSpanAttributes.SPAN_FUNCTION_NAME)
        )
        assertTrue(
            trace.getAttribute(FluentSpanAttributes.SPAN_SOURCE_NAME)?.endsWith("MyTestClass") ?: false
        )
        assertEquals(
            trace.getAttribute(FluentSpanAttributes.SPAN_INPUTS),
            "{\"paramName\":10}"
        )
        assertEquals(
            trace.getAttribute(FluentSpanAttributes.SPAN_OUTPUTS),
            result.toString()
        )
        assertEquals(
            "mySpanType",
            trace.getAttribute(FluentSpanAttributes.SPAN_TYPE)
        )
    }

    @Test
    fun `test multiple trace creation`() = runTest {
        val experimentId = createExperimentId()
        withProjectId(experimentId) {
            val testClass = MyTestClass()
            testClass.testFunction(1)
            testClass.anotherTestFunction("OpenTelemetry")
        }

        val traces = getTraces(experimentId)

        assertEquals(2, traces.size)
        assertNotEquals(
            traces.first(),
            traces.last(),
            message = "Trace IDs should be unique."
        )
    }

    @Test
    fun `test parent child trace`() = runTest {
        val experimentId = createExperimentId()
        withProjectId(experimentId) {
            MyTestClass().parentTestFunction("RandomString")
        }

        val traces = getTraces(experimentId)

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
        val experimentId = createExperimentId()
        withProjectId(experimentId) {
            assertThrows<RuntimeException> {
                MyTestClass().testFunctionThrows(3)
            }
        }

        val traces = getTraces(experimentId)

        assertEquals(1, traces.size)
        val trace = traces.firstOrNull() as? SpanData
        assertNotNull(trace)

        assertEquals(StatusCode.ERROR, trace.status.statusCode)
    }
}
