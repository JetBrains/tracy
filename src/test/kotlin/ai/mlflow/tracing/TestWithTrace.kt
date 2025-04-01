package ai.mlflow.tracing

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.example.ai.mlflow.KotlinMlflowClient
import org.example.ai.mlflow.fluent.KotlinFlowTrace
import org.example.ai.mlflow.fluent.processor.withTraceSuspended
import org.example.ai.mlflow.getTraces
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

internal class MyTestClassWithSuspendAndTrace {
    @KotlinFlowTrace(name = "Main Span", spanType = "mySpanType")
    suspend fun testFunction(paramName: Int): Int = withTraceSuspended(spanName = "Main Span") {
        delay(12)
        return@withTraceSuspended paramName
    }
}

class TestSuspendFunctionsWithTraceTracing: MlflowTracingTests() {
    @Test
    fun `test trace creation`() = runBlocking {
        MyTestClassWithSuspendAndTrace().testFunction(1)
        val tracesResponse = runBlocking {
            getTraces(listOf(KotlinMlflowClient.currentExperimentId))
        }

        assertEquals(1, tracesResponse.traces.size)
        val trace = tracesResponse.traces.first()
        assertNotNull(trace)
        assertEquals(KotlinMlflowClient.currentExperimentId, trace.experimentId)
    }
}