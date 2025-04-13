package ai.dev.kit.providers.mlflow.tracing.dumb

import kotlinx.coroutines.runBlocking
import ai.dev.kit.core.fluent.KotlinFlowTrace
import ai.dev.kit.core.fluent.processor.withTrace
import ai.dev.kit.providers.mlflow.KotlinMlflowClient
import ai.dev.kit.providers.mlflow.fluent.MlflowTracingMetadataConfigurator
import ai.dev.kit.providers.mlflow.getTraces
import ai.dev.kit.providers.mlflow.tracing.MlflowTracingTests
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

internal class MyTestClassDumb {
    @KotlinFlowTrace(name = "Main Span", spanType = "mySpanType")
    fun testFunction(paramName: Int): Int = withTrace(
        function = ::testFunction,
        args = arrayOf<Any?>(paramName),
        tracingMetadataConfigurator = MlflowTracingMetadataConfigurator
    ) {
        return@withTrace paramName
    }

    @KotlinFlowTrace(name = "Secondary Span", spanType = "func")
    fun anotherTestFunction(x: String): String = withTrace(
        function = ::anotherTestFunction,
        args = arrayOf<Any?>(x),
        tracingMetadataConfigurator = MlflowTracingMetadataConfigurator
    ) {
        return@withTrace x.reversed()
    }

    @KotlinFlowTrace(name = "Parent Span")
    fun parentTestFunction(x: String): String = withTrace(
        function = ::parentTestFunction,
        args = arrayOf<Any?>(x),
        tracingMetadataConfigurator = MlflowTracingMetadataConfigurator
    ) {
        return@withTrace childTestFunction(x.reversed())
    }

    @KotlinFlowTrace(name = "Child Span")
    fun childTestFunction(x: String): String = withTrace(
        function = ::childTestFunction,
        args = arrayOf<Any?>(x),
        tracingMetadataConfigurator = MlflowTracingMetadataConfigurator
    ) {
        return@withTrace x.reversed()
    }
}

class TestDumbFluentTracing : MlflowTracingTests() {
    @Test
    fun `test trace creation`() {
        MyTestClassDumb().testFunction(1)
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
        val testClass = MyTestClassDumb()
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
        val testClass = MyTestClassDumb()
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
        MyTestClassDumb().parentTestFunction("RandomString")

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
