package ai.jetbrains.tracy.core.fluent

import ai.jetbrains.tracy.test.utils.BaseOpenTelemetryTracingTest
import ai.jetbrains.tracy.core.addLangfuseTagsToCurrentTrace
import io.opentelemetry.api.trace.SpanId
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.sdk.internal.ExceptionAttributeResolver
import io.opentelemetry.sdk.trace.data.ExceptionEventData
import io.opentelemetry.sdk.trace.data.StatusData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class MyTestClassWithSuspend {
    @Trace(name = "Main Span")
    suspend fun testFunction(paramName: Int): Int {
        delay(12)
        return paramName
    }

    @Trace(name = "Main Span")
    suspend fun testFunctionWithTag(paramName: Int): Int {
        delay(12)
        addLangfuseTagsToCurrentTrace(listOf("Tag1", "Tag2"), currentCoroutineContext())
        return paramName
    }

    @Trace(name = "Throws")
    suspend fun testFunctionThrows(paramName: Int): Int {
        delay(42)
        throw RuntimeException("Test exception")
    }

    @Trace(name = "Secondary Span")
    suspend fun anotherTestFunction(x: String): String {
        delay(45)
        return x.reversed()
    }

    @Trace(name = "Parent Span")
    suspend fun parentTestFunction(x: String): String {
        delay(50)
        return childTestFunction(x.reversed())
    }

    @Trace(name = "Child Span")
    suspend fun childTestFunction(x: String): String {
        delay(10)
        val result = x.reversed()
        return result
    }

    @Trace(name = "Parent Span Non-Suspend")
    suspend fun parentTestFunctionWithNonSuspendKidWithDispatcher(x: String): String {
        delay(50)
        return withContext(Dispatchers.IO) { childTestFunctionNonSuspendWithDispatcher(x.reversed()) }
    }

    @Trace(name = "Child Span Non Suspend")
    fun childTestFunctionNonSuspendWithDispatcher(x: String): String {
        return x.reversed()
    }

    @Trace(name = "Parent Span Non-Suspend")
    suspend fun parentTestFunctionWithNonSuspendKid(x: String): String {
        delay(50)
        return childTestFunctionNonSuspend(x.reversed())
    }

    @Trace(name = "Child Span Non Suspend")
    fun childTestFunctionNonSuspend(x: String): String {
        return x.reversed()
    }

    @Trace(name = "Parent Span Non-Suspend")
    fun parentTestFunctionWithSuspendKid(x: String): String {
        return runBlocking { childTestFunctionSuspend(x.reversed()) }
    }

    @Trace(name = "Child Span Non Suspend")
    suspend fun childTestFunctionSuspend(x: String): String {
        delay(10)
        return x.reversed()
    }

    @Trace(name = "Child Span")
    suspend fun testRecursion(level: Int): Int {
        delay(100)
        if (level == 1) return 0
        return testRecursion(level - 1)
    }
}

internal class MyTestClassWithSuspendHard() {
    @Trace(name = "P")
    suspend fun parentFunction(p: String): String {
        delay(100)
        // Calling Children
        val child1Result = childFunction1(p)
        val child2Result = childFunction2(p)
        val child3Result = childFunction3(p)

        return "$child1Result, $child2Result, $child3Result"
    }

    @Trace(name = "C1")
    suspend fun childFunction1(p: String): String {
        delay(50)
        return p.uppercase()
    }

    @Trace(name = "C2")
    suspend fun childFunction2(p: String): String {
        delay(50)
        val grandChild1Result = grandChildFunction1(p)
        return grandChild1Result
    }

    @Trace(name = "C3")
    suspend fun childFunction3(p: String): String {
        delay(50)
        return p.reversed()
    }

    @Trace(name = "G1")
    suspend fun grandChildFunction1(p: String): String {
        delay(30)
        return "G(${p.length})"
    }
}

class SuspendFluentTracingTest() : BaseOpenTelemetryTracingTest() {
    @Test
    fun `test trace creation`() = runTest {
        MyTestClassWithSuspend().testFunction(1)

        val traces = analyzeSpans()
        assertEquals(1, traces.size)
        val trace = traces.firstOrNull()
        assertNotNull(trace)
    }

    @Test
    fun `test tag for current trace`() = runTest {
        MyTestClassWithSuspend().testFunctionWithTag(1)

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
    fun `test trace tags and metadata are correct`() = runTest {
        val testClass = MyTestClassWithSuspend()
        val arg = 3
        val result = testClass.testFunction(arg)

        val traces = analyzeSpans()

        assertEquals(1, traces.size)
        val trace = assertNotNull(traces.firstOrNull())
        assertEquals(StatusData.ok(), trace.status)
        assertTrue(
            trace.getAttribute(FluentSpanAttributes.CODE_FUNCTION_NAME)?.endsWith("MyTestClassWithSuspend.testFunction")
                ?: false
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
    fun `test multiple trace creation`() = runTest {
        val testClass = MyTestClassWithSuspend()
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
    fun `test parent child trace`() = runTest {
        MyTestClassWithSuspend().parentTestFunction("RandomString")

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
    fun `test parent child trace with non suspend child with dispatcher`() = runTest {
        MyTestClassWithSuspend().parentTestFunctionWithNonSuspendKidWithDispatcher("RandomString")

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
    fun `test parent child trace with non suspend child`() = runTest {
        MyTestClassWithSuspend().parentTestFunctionWithNonSuspendKid("RandomString")

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
    fun `test parent child trace with non suspend parent`() = runTest {
        MyTestClassWithSuspend().parentTestFunctionWithSuspendKid("RandomString")

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
    fun `test recursion`() = runTest {
        MyTestClassWithSuspend().testRecursion(2)

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
        assertEquals(
            parentTrace.getAttribute(FluentSpanAttributes.CODE_FUNCTION_NAME),
            childTrace.getAttribute(FluentSpanAttributes.CODE_FUNCTION_NAME)
        )
    }

    @Test
    fun `test parent and child trace hierarchy`() = runTest {
        val result = MyTestClassWithSuspendHard().parentFunction("a")

        val traces = analyzeSpans()

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
        assertThrows<RuntimeException> {
            MyTestClassWithSuspend().testFunctionThrows(3)
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
