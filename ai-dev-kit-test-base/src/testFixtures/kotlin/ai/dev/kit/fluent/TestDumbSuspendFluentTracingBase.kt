package ai.dev.kit.fluent

import ai.dev.kit.core.fluent.KotlinFlowTrace
import ai.dev.kit.core.fluent.KotlinLoggingClient
import ai.dev.kit.core.fluent.dataclasses.TracesResponse
import ai.dev.kit.core.fluent.processor.withTrace
import ai.dev.kit.core.fluent.processor.withTraceSuspended
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

internal class MyTestClassWithSuspendDumb {
    @KotlinFlowTrace(name = "Main Span", spanType = "mySpanType")
    suspend fun testFunction(paramName: Int): Int = withTraceSuspended(
        function = ::testFunction,
        args = arrayOf<Any?>(paramName),
    ) {
        delay(12)
        return@withTraceSuspended paramName
    }

    @KotlinFlowTrace(name = "Secondary Span", spanType = "func")
    suspend fun anotherTestFunction(x: String): String = withTraceSuspended(
        function = ::anotherTestFunction,
        args = arrayOf<Any?>(x),
    ) {
        delay(45)
        return@withTraceSuspended x.reversed()
    }

    @KotlinFlowTrace(name = "Parent Span")
    suspend fun parentTestFunction(x: String): String = withTraceSuspended(
        function = ::parentTestFunction,
        args = arrayOf<Any?>(x),
    ) {
        delay(50)
        return@withTraceSuspended childTestFunction(x.reversed())
    }

    @KotlinFlowTrace(name = "Child Span")
    suspend fun childTestFunction(x: String): String = withTraceSuspended(
        function = ::childTestFunction,
        args = arrayOf<Any?>(x),
    ) {
        delay(10)
        val result = x.reversed()
        return@withTraceSuspended result
    }

    @KotlinFlowTrace(name = "Parent Span Non Suspend")
    suspend fun parentTestFunctionWithNonSuspendKid(x: String): String = withTraceSuspended(
        function = ::parentTestFunctionWithNonSuspendKid,
        args = arrayOf<Any?>(x),
    ) {
        delay(50)
        return@withTraceSuspended childTestFunctionNonSuspend(x.reversed())
    }

    @KotlinFlowTrace(name = "Child Span Non Suspend")
    fun childTestFunctionNonSuspend(x: String): String = withTrace(
        function = ::childTestFunctionNonSuspend,
        args = arrayOf<Any?>(x),
    ) {
        return@withTrace x.reversed()
    }

    @KotlinFlowTrace(name = "Parent Span Non Suspend")
    fun parentTestFunctionWithSuspendKid(x: String): String = withTrace(
        function = ::parentTestFunctionWithSuspendKid,
        args = arrayOf<Any?>(x),
    ) {
        return@withTrace runBlocking { childTestFunctionSuspend(x.reversed()) }
    }

    @KotlinFlowTrace(name = "Child Span Non Suspend")
    suspend fun childTestFunctionSuspend(x: String): String = withTraceSuspended(
        function = ::childTestFunctionSuspend,
        args = arrayOf<Any?>(x),
    ) {
        delay(10)
        return@withTraceSuspended x.reversed()
    }

    @KotlinFlowTrace(name = "Child Span")
    suspend fun testRecursion(level: Int): Int = withTraceSuspended(
        function = ::testRecursion,
        args = arrayOf<Any?>(level),
    ) {
        delay(100)
        if (level == 0) return@withTraceSuspended 0
        return@withTraceSuspended testRecursion(level - 1)
    }
}

internal class MyTestClassWithSuspendDumbHard() {
    @KotlinFlowTrace(name = "P", spanType = "P")
    suspend fun parentFunction(p: String): String = withTraceSuspended(
        function = ::parentFunction,
        args = arrayOf<Any?>(p),
    ) {
        delay(100)
        // Calling Children
        val child1Result = childFunction1(p)
        val child2Result = childFunction2(p)
        val child3Result = childFunction3(p)

        return@withTraceSuspended "$child1Result, $child2Result, $child3Result"
    }

    @KotlinFlowTrace(name = "C1", spanType = "C")
    suspend fun childFunction1(p: String): String = withTraceSuspended(
        function = ::childFunction1,
        args = arrayOf<Any?>(p),
    ) {
        delay(50)
        return@withTraceSuspended p.uppercase()
    }

    @KotlinFlowTrace(name = "C2", spanType = "C")
    suspend fun childFunction2(p: String): String = withTraceSuspended(
        function = ::childFunction2,
        args = arrayOf<Any?>(p),
    ) {
        delay(50)
        val grandChild1Result = grandChildFunction1(p)
        return@withTraceSuspended grandChild1Result
    }

    @KotlinFlowTrace(name = "C3", spanType = "C")
    suspend fun childFunction3(p: String): String = withTraceSuspended(
        function = ::childFunction3,
        args = arrayOf<Any?>(p),
    ) {
        delay(50)
        return@withTraceSuspended p.reversed()
    }

    @KotlinFlowTrace(name = "G1", spanType = "G")
    suspend fun grandChildFunction1(p: String): String = withTraceSuspended(
        function = ::grandChildFunction1,
        args = arrayOf<Any?>(p),
    ) {
        delay(30)
        return@withTraceSuspended "G(${p.length})"
    }
}

open class TestDumbSuspendFluentTracingBase(
    val getTraces: KSuspendFunction1<List<String>, TracesResponse>,
    private val client: KotlinLoggingClient
) {
    @Test
    fun `test trace creation`() = runBlocking {
        MyTestClassWithSuspendDumb().testFunction(1)
        val tracesResponse = getTraces(listOf(client.currentExperimentId))

        assertEquals(1, tracesResponse.traces.size)
        val trace = tracesResponse.traces.first()
        assertNotNull(trace)
        assertEquals(client.currentExperimentId, trace.experimentId)
    }

    @Test
    fun `test trace tags and metadata are correct`() = runBlocking {
        val testClass = MyTestClassWithSuspendDumb()
        val arg = 3
        val result = testClass.testFunction(arg)

        val tracesResponse = getTraces(listOf(client.currentExperimentId))
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
        val testClass = MyTestClassWithSuspendDumb()
        testClass.testFunction(1)
        testClass.anotherTestFunction("OpenTelemetry")

        val tracesResponse = getTraces(listOf(client.currentExperimentId))

        assertEquals(2, tracesResponse.traces.size)

        val firstTrace = tracesResponse.traces[0]
        val secondTrace = tracesResponse.traces[1]

        assertNotEquals(firstTrace.requestId, secondTrace.requestId, "Trace IDs should be unique.")
    }

    @Test
    fun `test parent child trace`() = runBlocking {
        MyTestClassWithSuspendDumb().parentTestFunction("RandomString")

        val tracesResponse = getTraces(listOf(client.currentExperimentId))

        var trace = tracesResponse.traces.firstOrNull()
        trace = assertNotNull(trace)

        assertEquals(
            "[{\"name\":\"Child Span\",\"type\":\"UNKNOWN\",\"inputs\":\"{\\\"x\\\":\\\"gnirtSmodnaR\\\"}\"},{\"name\":\"Parent Span\",\"type\":\"UNKNOWN\",\"inputs\":\"{\\\"x\\\":\\\"RandomString\\\"}\"}]",
            trace.tags.firstOrNull { it.key == "traceSpans" }?.value ?: ""
        )
    }

    @Test
    fun `test parent child trace with non suspend child`() = runBlocking {
        MyTestClassWithSuspendDumb().parentTestFunctionWithNonSuspendKid("RandomString")

        val tracesResponse = getTraces(listOf(client.currentExperimentId))

        var trace = tracesResponse.traces.firstOrNull()
        trace = assertNotNull(trace)

        assertEquals(
            "[{\"name\":\"Child Span Non Suspend\",\"type\":\"UNKNOWN\",\"inputs\":\"{\\\"x\\\":\\\"gnirtSmodnaR\\\"}\"},{\"name\":\"Parent Span Non Suspend\",\"type\":\"UNKNOWN\",\"inputs\":\"{\\\"x\\\":\\\"RandomString\\\"}\"}]",
            trace.tags.firstOrNull { it.key == "traceSpans" }?.value ?: ""
        )
    }

    @Test
    fun `test parent child trace with non suspend parent`() = runBlocking {
        MyTestClassWithSuspendDumb().parentTestFunctionWithSuspendKid("RandomString")

        val tracesResponse = getTraces(listOf(client.currentExperimentId))
        var trace = tracesResponse.traces.firstOrNull()
        trace = assertNotNull(trace)

        assertEquals(
            "[{\"name\":\"Child Span Non Suspend\",\"type\":\"UNKNOWN\",\"inputs\":\"{\\\"x\\\":\\\"gnirtSmodnaR\\\"}\"},{\"name\":\"Parent Span Non Suspend\",\"type\":\"UNKNOWN\",\"inputs\":\"{\\\"x\\\":\\\"RandomString\\\"}\"}]",
            trace.tags.firstOrNull { it.key == "traceSpans" }?.value ?: ""
        )
    }

    @Test
    fun `test recursion`() = runBlocking {
        MyTestClassWithSuspendDumb().testRecursion(2)

        val tracesResponse = getTraces(listOf(client.currentExperimentId))
        var trace = tracesResponse.traces.firstOrNull()
        trace = assertNotNull(trace)

        assertEquals(
            "[{\"name\":\"Child Span\",\"type\":\"UNKNOWN\",\"inputs\":\"{\\\"level\\\":0}\"},{\"name\":\"Child Span\",\"type\":\"UNKNOWN\",\"inputs\":\"{\\\"level\\\":1}\"},{\"name\":\"Child Span\",\"type\":\"UNKNOWN\",\"inputs\":\"{\\\"level\\\":2}\"}]",
            trace.tags.firstOrNull { it.key == "traceSpans" }?.value ?: ""
        )
    }

    @Test
    fun `test parent and child trace hierarchy`() = runBlocking {
        val result = MyTestClassWithSuspendDumbHard().parentFunction("a")

        val tracesResponse = getTraces(listOf(client.currentExperimentId))

        assertNotNull(tracesResponse)
        assertEquals(1, tracesResponse.traces.size)
        val trace = tracesResponse.traces.first()
        assertNotNull(trace)

        val expectedSpans = listOf(
            "{\"name\":\"C1\",\"type\":\"C\",\"inputs\":\"{\\\"p\\\":\\\"a\\\"}\"}",
            "{\"name\":\"G1\",\"type\":\"G\",\"inputs\":\"{\\\"p\\\":\\\"a\\\"}\"}",
            "{\"name\":\"G2\",\"type\":\"G\",\"inputs\":\"{\\\"p\\\":\\\"a\\\"}\"}",
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
