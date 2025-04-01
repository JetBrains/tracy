package ai.mlflow.tracing.dumb

import ai.core.fluent.KotlinFlowTrace
import ai.mlflow.tracing.MlflowTracingTests
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.example.ai.mlflow.KotlinMlflowClient
import ai.core.fluent.processor.withTrace
import ai.core.fluent.processor.withTraceSuspended
import org.example.ai.mlflow.getTraces
import ai.mlflow.fluent.MlflowTracingMetadataConfigurator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

internal class MyTestClassWithSuspendDumb {
    @KotlinFlowTrace(name = "Main Span", spanType = "mySpanType")
    suspend fun testFunction(paramName: Int): Int = withTraceSuspended(
        function = ::testFunction,
        args = arrayOf<Any?>(paramName),
        tracingMetadataConfigurator = MlflowTracingMetadataConfigurator
    ) {
        delay(12)
        return@withTraceSuspended paramName
    }

    @KotlinFlowTrace(name = "Secondary Span", spanType = "func")
    suspend fun anotherTestFunction(x: String): String = withTraceSuspended(
        function = ::anotherTestFunction,
        args = arrayOf<Any?>(x),
        tracingMetadataConfigurator = MlflowTracingMetadataConfigurator
    ) {
        delay(45)
        return@withTraceSuspended x.reversed()
    }

    @KotlinFlowTrace(name = "Parent Span")
    suspend fun parentTestFunction(x: String): String = withTraceSuspended(
        function = ::parentTestFunction,
        args = arrayOf<Any?>(x),
        tracingMetadataConfigurator = MlflowTracingMetadataConfigurator
    ) {
        delay(50)
        return@withTraceSuspended childTestFunction(x.reversed())
    }

    @KotlinFlowTrace(name = "Child Span")
    suspend fun childTestFunction(x: String): String = withTraceSuspended(
        function = ::childTestFunction,
        args = arrayOf<Any?>(x),
        tracingMetadataConfigurator = MlflowTracingMetadataConfigurator
    ) {
        delay(10)
        val result = x.reversed()
        return@withTraceSuspended result
    }

    @KotlinFlowTrace(name = "Parent Span Non Suspend")
    suspend fun parentTestFunctionWithNonSuspendKid(x: String): String = withTraceSuspended(
        function = ::parentTestFunctionWithNonSuspendKid,
        args = arrayOf<Any?>(x),
        tracingMetadataConfigurator = MlflowTracingMetadataConfigurator
    ) {
        delay(50)
        return@withTraceSuspended childTestFunctionNonSuspend(x.reversed())
    }

    @KotlinFlowTrace(name = "Child Span Non Suspend")
    fun childTestFunctionNonSuspend(x: String): String = withTrace(
        function = ::childTestFunctionNonSuspend,
        args = arrayOf<Any?>(x),
        tracingMetadataConfigurator = MlflowTracingMetadataConfigurator
    ) {
        return@withTrace x.reversed()
    }

    @KotlinFlowTrace(name = "Parent Span Non Suspend")
    fun parentTestFunctionWithSuspendKid(x: String): String = withTrace(
        function = ::parentTestFunctionWithSuspendKid,
        args = arrayOf<Any?>(x),
        tracingMetadataConfigurator = MlflowTracingMetadataConfigurator
    ) {
        return@withTrace runBlocking { childTestFunctionSuspend(x.reversed()) }
    }

    @KotlinFlowTrace(name = "Child Span Non Suspend")
    suspend fun childTestFunctionSuspend(x: String): String = withTraceSuspended(
        function = ::childTestFunctionSuspend,
        args = arrayOf<Any?>(x),
        tracingMetadataConfigurator = MlflowTracingMetadataConfigurator
    ) {
        delay(10)
        return@withTraceSuspended x.reversed()
    }

    @KotlinFlowTrace(name = "Child Span")
    suspend fun testRecursion(level: Int): Int = withTraceSuspended(
        function = ::testRecursion,
        args = arrayOf<Any?>(level),
        tracingMetadataConfigurator = MlflowTracingMetadataConfigurator
    ) {
        delay(100)
        if (level == 0) return@withTraceSuspended 0
        return@withTraceSuspended testRecursion(level - 1)
    }
}

internal class MyTestClassWithSuspendDumbHard {
    @KotlinFlowTrace(name = "Parent Span")
    suspend fun parentFunction(param: String): String = withTraceSuspended(
        function = ::parentFunction,
        args = arrayOf<Any?>(param),
        tracingMetadataConfigurator = MlflowTracingMetadataConfigurator
    ) {
        delay(100)
        // Calling Children
        val child1Result = childFunction1(param)
        val child2Result = childFunction2(param)
        val child3Result = childFunction3(param)

        return@withTraceSuspended "$child1Result, $child2Result, $child3Result"
    }

    @KotlinFlowTrace(name = "Child1 Span")
    suspend fun childFunction1(param: String): String = withTraceSuspended(
        function = ::childFunction1,
        args = arrayOf<Any?>(param),
        tracingMetadataConfigurator = MlflowTracingMetadataConfigurator
    ) {
        delay(50)
        return@withTraceSuspended param.uppercase()
    }

    @KotlinFlowTrace(name = "Child2 Span")
    suspend fun childFunction2(param: String): String = withTraceSuspended(
        function = ::childFunction2,
        args = arrayOf<Any?>(param),
        tracingMetadataConfigurator = MlflowTracingMetadataConfigurator
    ) {
        delay(50)
        val grandChild1Result = grandChildFunction1(param)
        val grandChild2Result = grandChildFunction2(param)
        return@withTraceSuspended "$grandChild1Result and $grandChild2Result"
    }

    @KotlinFlowTrace(name = "Child3 Span")
    suspend fun childFunction3(param: String): String = withTraceSuspended(
        function = ::childFunction3,
        args = arrayOf<Any?>(param),
        tracingMetadataConfigurator = MlflowTracingMetadataConfigurator
    ) {
        delay(50)
        return@withTraceSuspended param.reversed()
    }

    @KotlinFlowTrace(name = "GrandChild1 Span")
    suspend fun grandChildFunction1(param: String): String = withTraceSuspended(
        function = ::grandChildFunction1,
        args = arrayOf<Any?>(param),
        tracingMetadataConfigurator = MlflowTracingMetadataConfigurator
    ) {
        delay(30)
        return@withTraceSuspended "GrandChild1(${param.length})"
    }

    @KotlinFlowTrace(name = "GrandChild2 Span")
    suspend fun grandChildFunction2(param: String): String = withTraceSuspended(
        function = ::grandChildFunction2,
        args = arrayOf<Any?>(param),
        tracingMetadataConfigurator = MlflowTracingMetadataConfigurator
    ) {
        delay(30)
        return@withTraceSuspended "GrandChild2(${param.reversed()})"
    }
}


@KotlinFlowTrace(name = "Top Level Span")
internal fun topLevelTestFunction(x: String): String = withTrace(
    function = ::topLevelTestFunction,
    args = arrayOf<Any?>(x),
    tracingMetadataConfigurator = MlflowTracingMetadataConfigurator
) {
    return@withTrace x.reversed()
}

class TestDumbSuspendFluentTracing: MlflowTracingTests() {
    @Test
    fun `test trace creation`() = runBlocking {
        MyTestClassWithSuspendDumb().testFunction(1)
        val tracesResponse = getTraces(listOf(KotlinMlflowClient.currentExperimentId))

        assertEquals(1, tracesResponse.traces.size)
        val trace = tracesResponse.traces.first()
        assertNotNull(trace)
        assertEquals(KotlinMlflowClient.currentExperimentId, trace.experimentId)
    }

    @Test
    fun `test trace tags and metadata are correct`()  = runBlocking {
        val testClass = MyTestClassWithSuspendDumb()
        val arg = 3
        val result = testClass.testFunction(arg)

        val tracesResponse = getTraces(listOf(KotlinMlflowClient.currentExperimentId))
        var trace = tracesResponse.traces.firstOrNull()
        trace = assertNotNull(trace)

        assertEquals("OK", trace.status)
        assertEquals(
            "{\"paramName\":$arg}",
            trace.requestMetadata.firstOrNull { it.key == "mlflow.traceInputs" }?.value ?: ""
        )
        assertEquals(
            result.toString(),
            trace.requestMetadata.firstOrNull { it.key == "mlflow.traceOutputs" }?.value ?: ""
        )
        assertEquals(
            "Main Span",
            trace.tags.firstOrNull { it.key == "mlflow.traceName" }?.value ?: ""
        )
        assertEquals(
            "[{\"name\":\"Main Span\",\"type\":\"mySpanType\",\"inputs\":\"{\\\"paramName\\\":$arg}\"}]",
            trace.tags.firstOrNull { it.key == "mlflow.traceSpans" }?.value ?: ""
        )
    }

    @Test
    fun `test multiple trace creation`() = runBlocking {
        val testClass = MyTestClassWithSuspendDumb()
        testClass.testFunction(1)
        testClass.anotherTestFunction("OpenTelemetry")

        val tracesResponse = getTraces(listOf(KotlinMlflowClient.currentExperimentId))

        assertEquals(2, tracesResponse.traces.size)

        val firstTrace = tracesResponse.traces[0]
        val secondTrace = tracesResponse.traces[1]

        assertNotEquals(firstTrace.requestId, secondTrace.requestId, "Trace IDs should be unique.")
    }

    @Test
    fun `test parent child trace`() = runBlocking {
        MyTestClassWithSuspendDumb().parentTestFunction("RandomString")

        val tracesResponse = getTraces(listOf(KotlinMlflowClient.currentExperimentId))

        var trace = tracesResponse.traces.firstOrNull()
        trace = assertNotNull(trace)

        assertEquals(
            "[{\"name\":\"Child Span\",\"type\":\"UNKNOWN\",\"inputs\":\"{\\\"x\\\":\\\"gnirtSmodnaR\\\"}\"},{\"name\":\"Parent Span\",\"type\":\"UNKNOWN\",\"inputs\":\"{\\\"x\\\":\\\"RandomString\\\"}\"}]",
            trace.tags.firstOrNull { it.key == "mlflow.traceSpans" }?.value ?: ""
        )
    }

    @Test
    fun `test parent child trace with non suspend child`() = runBlocking {
        MyTestClassWithSuspendDumb().parentTestFunctionWithNonSuspendKid("RandomString")

        val tracesResponse = getTraces(listOf(KotlinMlflowClient.currentExperimentId))

        var trace = tracesResponse.traces.firstOrNull()
        trace = assertNotNull(trace)

        assertEquals(
            "[{\"name\":\"Child Span Non Suspend\",\"type\":\"UNKNOWN\",\"inputs\":\"{\\\"x\\\":\\\"gnirtSmodnaR\\\"}\"},{\"name\":\"Parent Span Non Suspend\",\"type\":\"UNKNOWN\",\"inputs\":\"{\\\"x\\\":\\\"RandomString\\\"}\"}]",
            trace.tags.firstOrNull { it.key == "mlflow.traceSpans" }?.value ?: ""
        )
    }

    @Test
    fun `test parent child trace with non suspend parent`() = runBlocking {
        MyTestClassWithSuspendDumb().parentTestFunctionWithSuspendKid("RandomString")

        val tracesResponse = getTraces(listOf(KotlinMlflowClient.currentExperimentId))
        var trace = tracesResponse.traces.firstOrNull()
        trace = assertNotNull(trace)

        assertEquals(
            "[{\"name\":\"Child Span Non Suspend\",\"type\":\"UNKNOWN\",\"inputs\":\"{\\\"x\\\":\\\"gnirtSmodnaR\\\"}\"},{\"name\":\"Parent Span Non Suspend\",\"type\":\"UNKNOWN\",\"inputs\":\"{\\\"x\\\":\\\"RandomString\\\"}\"}]",
            trace.tags.firstOrNull { it.key == "mlflow.traceSpans" }?.value ?: ""
        )
    }

    @Test
    fun `test recursion`() = runBlocking {
        MyTestClassWithSuspendDumb().testRecursion(2)

        val tracesResponse = getTraces(listOf(KotlinMlflowClient.currentExperimentId))
        var trace = tracesResponse.traces.firstOrNull()
        trace = assertNotNull(trace)

        assertEquals(
            "[{\"name\":\"Child Span\",\"type\":\"UNKNOWN\",\"inputs\":\"{\\\"level\\\":0}\"},{\"name\":\"Child Span\",\"type\":\"UNKNOWN\",\"inputs\":\"{\\\"level\\\":1}\"},{\"name\":\"Child Span\",\"type\":\"UNKNOWN\",\"inputs\":\"{\\\"level\\\":2}\"}]",
            trace.tags.firstOrNull { it.key == "mlflow.traceSpans" }?.value ?: ""
        )
    }

    @Test
    fun `test top level function`() = runBlocking {
        topLevelTestFunction("RandomString")

        val tracesResponse = getTraces(listOf(KotlinMlflowClient.currentExperimentId))

        assertEquals(1, tracesResponse.traces.size)
        val trace = tracesResponse.traces.first()
        assertNotNull(trace)
        assertEquals(KotlinMlflowClient.currentExperimentId, trace.experimentId)
    }

    @Test
    fun `test parent and child trace hierarchy`() = runBlocking {
        val result = MyTestClassWithSuspendDumbHard().parentFunction("TestInput")

        val tracesResponse = getTraces(listOf(KotlinMlflowClient.currentExperimentId))

        assertNotNull(tracesResponse)
        assertEquals(1, tracesResponse.traces.size)
        val trace = tracesResponse.traces.first()
        assertNotNull(trace)
        val expectedSpans = "[{\"name\":\"Child1 Span\",\"type\":\"UNKNOWN\",\"inputs\":\"{\\\"param\\\":\\\"TestInput\\\"}\"},{\"name\":\"GrandChild1 Span\",\"type\":\"UNKNOWN\",\"inputs\":\"{\\\"param\\\":\\\"TestInput\\\"}\"},{\"name\":\"GrandChild2 Span\",\"type\":\"UNKNOWN\",\"inputs\":\"{\\\"param\\\":\\\"TestInput\\\"}\"},{\"name\":"

        assertEquals(
            expectedSpans,
            trace.tags.firstOrNull { it.key == "mlflow.traceSpans" }?.value ?: ""
        )

        val expectedResult = "TESTINPUT, GrandChild1(9) and GrandChild2(tupnItseT), tupnItseT"
        assertEquals(expectedResult, result)
    }

}
