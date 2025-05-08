package ai.dev.kit.fluent

import ai.dev.kit.tracing.fluent.KotlinFlowTrace
import ai.dev.kit.tracing.fluent.KotlinLoggingClient
import ai.dev.kit.tracing.fluent.dataclasses.TracesResponse
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.reflect.KSuspendFunction1
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

internal class MyTestClass {
    @KotlinFlowTrace(name = "Main Span", spanType = "mySpanType")
    fun testFunction(paramName: Int): Int {
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

@KotlinFlowTrace(name = "Top Level Span")
internal fun topLevelTestFunction(x: String): String {
    return x.reversed()
}

@KotlinFlowTrace()
fun List<String>.foo(): String {
    return this.joinToString(" ")
}

open class TestFluentTracingBase(
    val getTraces: KSuspendFunction1<String, TracesResponse>,
    private val client: KotlinLoggingClient
) {
    @Test
    fun `test trace creation`() {
        MyTestClass().testFunction(1)
        val tracesResponse = runBlocking {
            getTraces(client.currentExperimentId)
        }

        assertEquals(1, tracesResponse.traces.size)
        val trace = tracesResponse.traces.first()
        assertNotNull(trace)
        assertEquals(client.currentExperimentId, trace.experimentId)
    }

    @Test
    fun `test extension function`() {
        val result = listOf("first", "second").foo()
        val tracesResponse = runBlocking {
            getTraces(client.currentExperimentId)
        }

        assertEquals("first second", result)
        assertEquals(1, tracesResponse.traces.size)
        val trace = tracesResponse.traces.first()
        assertNotNull(trace)
        assertEquals(client.currentExperimentId, trace.experimentId)
    }

    @Test
    fun `test top level function`() = runBlocking {
        topLevelTestFunction("RandomString")

        val tracesResponse = runBlocking {
            getTraces(client.currentExperimentId)
        }

        assertEquals(1, tracesResponse.traces.size)
        val trace = tracesResponse.traces.first()
        assertNotNull(trace)
        assertEquals(client.currentExperimentId, trace.experimentId)
    }

    @Test
    fun `test inside class function`() = runBlocking {
        MyTestClass.InsideClass().insideTestFunction("RandomString")

        val tracesResponse = runBlocking {
            getTraces(client.currentExperimentId)
        }

        assertEquals(1, tracesResponse.traces.size)
        val trace = tracesResponse.traces.first()
        assertNotNull(trace)
        assertEquals(client.currentExperimentId, trace.experimentId)
    }

    @Test
    fun `test trace tags and metadata are correct`() {
        val testClass = MyTestClass()
        val arg = 3
        val result = testClass.testFunction(arg)

        val tracesResponse = runBlocking {
            getTraces(client.currentExperimentId)
        }
        var trace = tracesResponse.traces.firstOrNull()
        trace = assertNotNull(trace)

        assertEquals("OK", trace.status)
        assertEquals(
            "{\"paramName\":$arg}",
            trace.requestMetadata.firstOrNull { it.key == "traceInputs" }?.value ?: ""
        )
        assertEquals(
            result.toString(),
            trace.requestMetadata.firstOrNull { it.key == "traceOutputs" }?.value ?: ""
        )
        assertEquals(
            "Main Span",
            trace.tags.firstOrNull { it.key == "traceName" }?.value ?: ""
        )
        assertEquals(
            "[{\"name\":\"Main Span\",\"type\":\"mySpanType\",\"inputs\":\"{\\\"paramName\\\":$arg}\"}]",
            trace.tags.firstOrNull { it.key == "traceSpans" }?.value ?: ""
        )
    }

    @Test
    fun `test trace params default values are correct`() {
        val testClass = MyTestClass()
        val result = testClass.testFunctionWithDefaultValue()

        val tracesResponse = runBlocking {
            getTraces(client.currentExperimentId)
        }
        var trace = tracesResponse.traces.firstOrNull()
        trace = assertNotNull(trace)

        assertEquals("OK", trace.status)
        assertEquals(
            "{\"paramName\":10}",
            trace.requestMetadata.firstOrNull { it.key == "traceInputs" }?.value ?: ""
        )
        assertEquals(
            result.toString(),
            trace.requestMetadata.firstOrNull { it.key == "traceOutputs" }?.value ?: ""
        )
        assertEquals(
            "Main Span",
            trace.tags.firstOrNull { it.key == "traceName" }?.value ?: ""
        )
        assertEquals(
            "[{\"name\":\"Main Span\",\"type\":\"mySpanType\",\"inputs\":\"{\\\"paramName\\\":10}\"}]",
            trace.tags.firstOrNull { it.key == "traceSpans" }?.value ?: ""
        )
    }

    @Test
    fun `test multiple trace creation`() {
        val testClass = MyTestClass()
        testClass.testFunction(1)
        testClass.anotherTestFunction("OpenTelemetry")

        val tracesResponse = runBlocking {
            getTraces(client.currentExperimentId)
        }

        assertEquals(2, tracesResponse.traces.size)

        val firstTrace = tracesResponse.traces[0]
        val secondTrace = tracesResponse.traces[1]

        assertNotEquals(firstTrace.requestId, secondTrace.requestId, "Trace IDs should be unique.")
    }

    @Test
    fun `test parent child trace`() {
        MyTestClass().parentTestFunction("RandomString")

        val tracesResponse = runBlocking {
            getTraces(client.currentExperimentId)
        }

        var trace = tracesResponse.traces.firstOrNull()
        trace = assertNotNull(trace)

        assertEquals(
            "[{\"name\":\"Child Span\",\"type\":\"UNKNOWN\",\"inputs\":\"{\\\"x\\\":\\\"gnirtSmodnaR\\\"}\"},{\"name\":\"Parent Span\",\"type\":\"UNKNOWN\",\"inputs\":\"{\\\"x\\\":\\\"RandomString\\\"}\"}]",
            trace.tags.firstOrNull { it.key == "traceSpans" }?.value ?: ""
        )
    }
}
