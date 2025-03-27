package ai.mlflow.tracing

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.example.ai.mlflow.KotlinMlflowClient
import org.example.ai.mlflow.fluent.KotlinFlowTrace
import org.example.ai.mlflow.getTraces
import kotlin.test.Test
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
        return x.reversed()
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

    @KotlinFlowTrace(name = "Child Span")
    suspend fun testRecursion(level: Int): Int {
        delay(10)
        if (level == 0) return 0
        return testRecursion(level - 1)
    }
}


class TestSuspendFunctionsTracing: MlflowTracingTests() {
    @Test
    fun `test trace creation`() = runBlocking {
        MyTestClassWithSuspend().testFunction(1)
        val tracesResponse = runBlocking {
            getTraces(listOf(KotlinMlflowClient.currentExperimentId))
        }

        assertEquals(1, tracesResponse.traces.size)
        val trace = tracesResponse.traces.first()
        assertNotNull(trace)
        assertEquals(KotlinMlflowClient.currentExperimentId, trace.experimentId)
    }

    @Test
    fun `test trace tags and metadata are correct`()  = runBlocking {
        val testClass = MyTestClassWithSuspend()
        val arg = 3
        val result = testClass.testFunction(arg)

        val tracesResponse = runBlocking {
            getTraces(listOf(KotlinMlflowClient.currentExperimentId))
        }
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
        val testClass = MyTestClassWithSuspend()
        testClass.testFunction(1)
        testClass.anotherTestFunction("OpenTelemetry")

        val tracesResponse = runBlocking {
            getTraces(listOf(KotlinMlflowClient.currentExperimentId))
        }

        assertEquals(2, tracesResponse.traces.size)

        val firstTrace = tracesResponse.traces[0]
        val secondTrace = tracesResponse.traces[1]

        assertNotEquals(firstTrace.requestId, secondTrace.requestId, "Trace IDs should be unique.")
    }

    @Test
    fun `test parent child trace`() = runBlocking {
        MyTestClassWithSuspend().parentTestFunction("RandomString")

        val tracesResponse = runBlocking {
            getTraces(listOf(KotlinMlflowClient.currentExperimentId))
        }

        var trace = tracesResponse.traces.firstOrNull()
        trace = assertNotNull(trace)

        assertEquals(
            "[{\"name\":\"Child Span\",\"type\":\"UNKNOWN\",\"inputs\":\"{\\\"x\\\":\\\"gnirtSmodnaR\\\"}\"},{\"name\":\"Parent Span\",\"type\":\"UNKNOWN\",\"inputs\":\"{\\\"x\\\":\\\"RandomString\\\"}\"}]",
            trace.tags.firstOrNull { it.key == "mlflow.traceSpans" }?.value ?: ""
        )
    }

    @Test
    fun `test parent child trace with non suspend`() = runBlocking {
        MyTestClassWithSuspend().parentTestFunctionWithNonSuspendKid("RandomString")

        val tracesResponse = runBlocking {
            getTraces(listOf(KotlinMlflowClient.currentExperimentId))
        }

        var trace = tracesResponse.traces.firstOrNull()
        trace = assertNotNull(trace)

        assertEquals(
            "[{\"name\":\"Child Span Non Suspend\",\"type\":\"UNKNOWN\",\"inputs\":\"{\\\"x\\\":\\\"gnirtSmodnaR\\\"}\"},{\"name\":\"Parent Span Non Suspend\",\"type\":\"UNKNOWN\",\"inputs\":\"{\\\"x\\\":\\\"RandomString\\\"}\"}]",
            trace.tags.firstOrNull { it.key == "mlflow.traceSpans" }?.value ?: ""
        )
    }

    @Test
    fun `test recursion`() = runBlocking {
        MyTestClassWithSuspend().testRecursion(2)

        val tracesResponse = runBlocking {
            getTraces(listOf(KotlinMlflowClient.currentExperimentId))
        }

        var trace = tracesResponse.traces.firstOrNull()
        trace = assertNotNull(trace)

        assertEquals(
            "[{\"name\":\"Child Span\",\"type\":\"UNKNOWN\",\"inputs\":\"{\\\"level\\\":0}\"},{\"name\":\"Child Span\",\"type\":\"UNKNOWN\",\"inputs\":\"{\\\"level\\\":1}\"},{\"name\":\"Child Span\",\"type\":\"UNKNOWN\",\"inputs\":\"{\\\"level\\\":2}\"}]",
            trace.tags.firstOrNull { it.key == "mlflow.traceSpans" }?.value ?: ""
        )
    }
}
