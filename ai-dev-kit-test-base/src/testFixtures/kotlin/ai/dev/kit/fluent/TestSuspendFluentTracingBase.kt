package ai.dev.kit.fluent

import ai.dev.kit.core.fluent.KotlinFlowTrace
import ai.dev.kit.core.fluent.KotlinLoggingClient
import ai.dev.kit.core.fluent.dataclasses.TracesResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import org.junit.jupiter.api.Test
import kotlin.reflect.KSuspendFunction1
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

internal class MyTestClassWithSuspend {
    @KotlinFlowTrace(name = "Main Span", spanType = "mySpanType")
    suspend fun testFunction(paramName: Int): Int {
        delay(12)
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
        if (level == 0) return 0
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

open class TestSuspendFluentTracingBase(
    val getTraces: KSuspendFunction1<List<String>, TracesResponse>,
    private val client: KotlinLoggingClient
) {
    @Test
    fun `test trace creation`() = runBlocking {
        MyTestClassWithSuspend().testFunction(1)
        val tracesResponse = getTraces(listOf(client.currentExperimentId))

        assertEquals(1, tracesResponse.traces.size)
        val trace = tracesResponse.traces.first()
        assertNotNull(trace)
        assertEquals(client.currentExperimentId, trace.experimentId) // todo: think
    }

    @Test
    fun `test trace tags and metadata are correct`() = runBlocking {
        val testClass = MyTestClassWithSuspend()
        val arg = 3
        val result = testClass.testFunction(arg)

        val tracesResponse = getTraces(client.currentExperimentId)
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
    fun `test multiple trace creation`() = runBlocking {
        val testClass = MyTestClassWithSuspend()
        testClass.testFunction(1)
        testClass.anotherTestFunction("OpenTelemetry")

        val tracesResponse = getTraces(client.currentExperimentId)

        assertEquals(2, tracesResponse.traces.size)

        val firstTrace = tracesResponse.traces[0]
        val secondTrace = tracesResponse.traces[1]

        assertNotEquals(firstTrace.requestId, secondTrace.requestId, "Trace IDs should be unique.")
    }

    @Test
    fun `test parent child trace`() = runBlocking {
        MyTestClassWithSuspend().parentTestFunction("RandomString")

        val tracesResponse = getTraces(client.currentExperimentId)

        var trace = tracesResponse.traces.firstOrNull()
        trace = assertNotNull(trace)

        assertEquals(
            "[{\"name\":\"Child Span\",\"type\":\"UNKNOWN\",\"inputs\":\"{\\\"x\\\":\\\"gnirtSmodnaR\\\"}\"},{\"name\":\"Parent Span\",\"type\":\"UNKNOWN\",\"inputs\":\"{\\\"x\\\":\\\"RandomString\\\"}\"}]",
            trace.tags.firstOrNull { it.key == "traceSpans" }?.value ?: ""
        )
    }

    @Test
    fun `test parent child trace with non suspend child`() = runBlocking {
        MyTestClassWithSuspend().parentTestFunctionWithNonSuspendKid("RandomString")

        val tracesResponse = getTraces(client.currentExperimentId)

        var trace = tracesResponse.traces.firstOrNull()
        trace = assertNotNull(trace)

        assertEquals(
            "[{\"name\":\"Child Span Non Suspend\",\"type\":\"UNKNOWN\",\"inputs\":\"{\\\"x\\\":\\\"gnirtSmodnaR\\\"}\"},{\"name\":\"Parent Span Non Suspend\",\"type\":\"UNKNOWN\",\"inputs\":\"{\\\"x\\\":\\\"RandomString\\\"}\"}]",
            trace.tags.firstOrNull { it.key == "traceSpans" }?.value ?: ""
        )
    }

    @Test
    fun `test parent child trace with non suspend parent`() = runBlocking {
        MyTestClassWithSuspend().parentTestFunctionWithSuspendKid("RandomString")

        val tracesResponse = getTraces(client.currentExperimentId)
        var trace = tracesResponse.traces.firstOrNull()
        trace = assertNotNull(trace)

        assertEquals(
            "[{\"name\":\"Child Span Non Suspend\",\"type\":\"UNKNOWN\",\"inputs\":\"{\\\"x\\\":\\\"gnirtSmodnaR\\\"}\"},{\"name\":\"Parent Span Non Suspend\",\"type\":\"UNKNOWN\",\"inputs\":\"{\\\"x\\\":\\\"RandomString\\\"}\"}]",
            trace.tags.firstOrNull { it.key == "traceSpans" }?.value ?: ""
        )
    }

    @Test
    fun `test recursion`() = runBlocking {
        MyTestClassWithSuspend().testRecursion(2)

        val tracesResponse = getTraces(client.currentExperimentId)
        var trace = tracesResponse.traces.firstOrNull()
        trace = assertNotNull(trace)

        assertEquals(
            "[{\"name\":\"Child Span\",\"type\":\"UNKNOWN\",\"inputs\":\"{\\\"level\\\":0}\"},{\"name\":\"Child Span\",\"type\":\"UNKNOWN\",\"inputs\":\"{\\\"level\\\":1}\"},{\"name\":\"Child Span\",\"type\":\"UNKNOWN\",\"inputs\":\"{\\\"level\\\":2}\"}]",
            trace.tags.firstOrNull { it.key == "traceSpans" }?.value ?: ""
        )
    }

    @Test
    fun `test parent and child trace hierarchy`() = runBlocking {
        val result = MyTestClassWithSuspendHard().parentFunction("a")

        val tracesResponse = getTraces(client.currentExperimentId)

        assertNotNull(tracesResponse)
        assertEquals(1, tracesResponse.traces.size)
        val trace = tracesResponse.traces.first()
        assertNotNull(trace)

        val expectedSpans = listOf(
            "{\"name\":\"C1\",\"type\":\"C\",\"inputs\":\"{\\\"p\\\":\\\"a\\\"}\"}",
            "{\"name\":\"G1\",\"type\":\"G\",\"inputs\":\"{\\\"p\\\":\\\"a\\\"}\"}",
            "{\"name\":\"C2\",\"type\":\"C\",\"inputs\":\"{\\\"p\\\":\\\"a\\\"}\"}",
            "{\"name\":\"C3\",\"type\":\"C\",\"inputs\":\"{\\\"p\\\":\\\"a\\\"}\"}",
            "{\"name\":\"P\",\"type\":\"P\",\"inputs\":\"{\\\"p\\\":\\\"a\\\"}\"}"
        )

        val spans = assertNotNull(trace.tags.firstOrNull { it.key == "traceSpans" }?.value)
        val actualSpans = Json.parseToJsonElement(spans).jsonArray

        actualSpans.forEach {
            // order of children/grandchildren is not guaranteed
            assertContains(expectedSpans, it.toString())
        }

        val expectedResult = "A, G(1), a"
        assertEquals(expectedResult, result)
    }
}
