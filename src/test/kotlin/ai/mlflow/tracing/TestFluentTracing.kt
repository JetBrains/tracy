package ai.mlflow.tracing

import kotlinx.coroutines.runBlocking
import org.example.ai.mlflow.KotlinMlflowClient
import org.example.ai.mlflow.fluent.KotlinFlowTrace
import org.example.ai.mlflow.getTraces
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

internal class MyTestClass {
    @KotlinFlowTrace(name = "Main Span", spanType = "mySpanType")
    fun testFunction(paramName: Int): Int {
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
}


class TestFluentTracing: MlflowTracingTests() {
    @Test
    fun `test trace creation`() {
        MyTestClass().testFunction(1)
        val tracesResponse = runBlocking {
            getTraces(listOf(KotlinMlflowClient.currentExperimentId))
        }

        assertEquals(1, tracesResponse.traces.size)
        val trace = tracesResponse.traces.first()
        assertNotNull(trace)
        assertEquals(KotlinMlflowClient.currentExperimentId, trace.experimentId)
    }

    @Test
    fun `test trace tags and metadata are correct`() {
        val testClass = MyTestClass()
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
    fun `test multiple trace creation`() {
        val testClass = MyTestClass()
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
    fun `test parent child trace`() {
        MyTestClass().parentTestFunction("RandomString")

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
}
