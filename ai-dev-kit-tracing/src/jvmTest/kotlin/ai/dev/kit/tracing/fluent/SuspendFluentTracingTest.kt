package ai.dev.kit.tracing.fluent

import ai.dev.kit.tracing.fluent.processor.TracingFlowProcessor
import io.opentelemetry.api.trace.SpanId
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.data.StatusData
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class MyTestClassWithSuspend {
    @KotlinFlowTrace(name = "Main Span", spanType = "mySpanType")
    suspend fun testFunction(paramName: Int): Int {
        delay(12)
        return paramName
    }

    @KotlinFlowTrace(name = "Throws")
    suspend fun testFunctionThrows(paramName: Int): Int {
        delay(42)
        throw RuntimeException("Test exception")
        return paramName
    }

    @KotlinFlowTrace(name = "Secondary Span", spanType = "func")
    suspend fun anotherTestFunction(x: String): String {
        delay(45)
        return x.reversed()
    }

    @KotlinFlowTrace(name = "Parent Span")
    suspend fun parentTestFunction(x: String): String {
        delay(50)
        return childTestFunction(x.reversed())
    }

    @KotlinFlowTrace(name = "Child Span")
    suspend fun childTestFunction(x: String): String {
        delay(10)
        val result = x.reversed()
        return result
    }

    @KotlinFlowTrace(name = "Parent Span Non Suspend")
    suspend fun parentTestFunctionWithNonSuspendKid(x: String): String {
        delay(50)
        return childTestFunctionNonSuspend(x.reversed())
    }

    @KotlinFlowTrace(name = "Child Span Non Suspend")
    fun childTestFunctionNonSuspend(x: String): String {
        return x.reversed()
    }

    @KotlinFlowTrace(name = "Parent Span Non Suspend")
    fun parentTestFunctionWithSuspendKid(x: String): String {
        return runBlocking { childTestFunctionSuspend(x.reversed()) }
    }

    @KotlinFlowTrace(name = "Child Span Non Suspend")
    suspend fun childTestFunctionSuspend(x: String): String {
        delay(10)
        return x.reversed()
    }

    @KotlinFlowTrace(name = "Child Span")
    suspend fun testRecursion(level: Int): Int {
        delay(100)
        if (level == 1) return 0
        return testRecursion(level - 1)
    }
}

internal class MyTestClassWithSuspendHard() {
    @KotlinFlowTrace(name = "P", spanType = "P")
    suspend fun parentFunction(p: String): String {
        delay(100)
        // Calling Children
        val child1Result = childFunction1(p)
        val child2Result = childFunction2(p)
        val child3Result = childFunction3(p)

        return "$child1Result, $child2Result, $child3Result"
    }

    @KotlinFlowTrace(name = "C1", spanType = "C")
    suspend fun childFunction1(p: String): String {
        delay(50)
        return p.uppercase()
    }

    @KotlinFlowTrace(name = "C2", spanType = "C")
    suspend fun childFunction2(p: String): String {
        delay(50)
        val grandChild1Result = grandChildFunction1(p)
        return grandChild1Result
    }

    @KotlinFlowTrace(name = "C3", spanType = "C")
    suspend fun childFunction3(p: String): String {
        delay(50)
        return p.reversed()
    }

    @KotlinFlowTrace(name = "G1", spanType = "G")
    suspend fun grandChildFunction1(p: String): String {
        delay(30)
        return "G(${p.length})"
    }
}

class SuspendFluentTracingTest() : BaseTracingTest() {
    @Test
    fun `test trace creation`() = runTest {
        val experimentId = createExperimentId()
        withProjectId(experimentId) {
            MyTestClassWithSuspend().testFunction(1)
        }

        TracingFlowProcessor.flushTraces()
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
            val testClass = MyTestClassWithSuspend()
            testClass.testFunction(arg)
        }

        val traces = getTraces(experimentId)

        assertEquals(1, traces.size)
        val trace = assertNotNull(traces.firstOrNull())

        assertEquals(StatusData.ok(), trace.status)
        assertEquals(
            "testFunction",
            trace.getAttribute(FluentSpanAttributes.SPAN_FUNCTION_NAME)
        )
        assertTrue(
            trace.getAttribute(FluentSpanAttributes.SPAN_SOURCE_NAME)?.endsWith("MyTestClassWithSuspend") ?: false
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
    fun `test multiple trace creation`() = runTest {
        val experimentId = createExperimentId()
        withProjectId(experimentId) {
            val testClass = MyTestClassWithSuspend()
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
            MyTestClassWithSuspend().parentTestFunction("RandomString")
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
    fun `test parent child trace with non suspend child`() = runTest {
        val experimentId = createExperimentId()
        withProjectId(experimentId) {
            MyTestClassWithSuspend().parentTestFunctionWithNonSuspendKid("RandomString")
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
    fun `test parent child trace with non suspend parent`() = runTest {
        val experimentId = createExperimentId()
        withProjectId(experimentId) {
            MyTestClassWithSuspend().parentTestFunctionWithSuspendKid("RandomString")
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
    fun `test recursion`() = runTest {
        val experimentId = createExperimentId()
        withProjectId(experimentId) {
            MyTestClassWithSuspend().testRecursion(2)
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
        assertEquals(
            parentTrace.getAttribute(FluentSpanAttributes.SPAN_FUNCTION_NAME),
            childTrace.getAttribute(FluentSpanAttributes.SPAN_FUNCTION_NAME)
        )
        assertEquals(
            parentTrace.getAttribute(FluentSpanAttributes.SPAN_SOURCE_NAME),
            childTrace.getAttribute(FluentSpanAttributes.SPAN_SOURCE_NAME)
        )
    }

    @Test
    fun `test parent and child trace hierarchy`() = runTest {
        val experimentId = createExperimentId()
        val result = withProjectId(experimentId) {
            MyTestClassWithSuspendHard().parentFunction("a")
        }

        val traces = getTraces(experimentId)

        assertEquals("A, G(1), a", result)

        val parentSpan = traces.find { it.name == "P" }
        val child1Span = traces.find { it.name == "C1" }
        val child2Span = traces.find { it.name == "C2" }
        val child3Span = traces.find { it.name == "C3" }
        val grandChildSpan = traces.find { it.name == "G1" }

        assertNotNull(parentSpan, "Parent span should exist")
        assertNotNull(child1Span, "Child1 span should exist")
        assertNotNull(child2Span, "Child2 span should exist")
        assertNotNull(child3Span, "Child3 span should exist")
        assertNotNull(grandChildSpan, "Grandchild span should exist")

        assertEquals(parentSpan.spanId, child1Span.parentSpanId, "C1 should be child of P")
        assertEquals(parentSpan.spanId, child2Span.parentSpanId, "C2 should be child of P")
        assertEquals(parentSpan.spanId, child3Span.parentSpanId, "C3 should be child of P")
        assertEquals(child2Span.spanId, grandChildSpan.parentSpanId, "G1 should be child of C2")

        val traceId = parentSpan.traceId
        traces.forEach {
            assertEquals(traceId, it.traceId, "All spans should share the same traceId")
        }

        traces.forEach {
            assertEquals(StatusData.ok(), it.status, "All spans should have OK status")
        }
    }

    @Test
    fun `test status is error, when function throws`() = runTest {
        val experimentId = createExperimentId()
        withProjectId(experimentId) {
            assertThrows<RuntimeException> {
                MyTestClassWithSuspend().testFunctionThrows(3)
            }
        }

        val traces = getTraces(experimentId)

        assertEquals(1, traces.size)
        val trace = traces.firstOrNull() as? SpanData
        assertNotNull(trace)

        assertEquals(StatusCode.ERROR, trace.status.statusCode)
    }
}
