package ai.dev.kit.providers.mlflow.tracing.dumb

import ai.dev.kit.core.fluent.KotlinFlowTrace
import ai.dev.kit.core.fluent.processor.withTrace
import ai.dev.kit.fluent.TestDumbAutologTracingBase
import ai.dev.kit.fluent.TestDumbFluentTracingBase
import ai.dev.kit.fluent.TestDumbSuspendFluentTracingBase
import ai.dev.kit.providers.mlflow.KotlinMlflowClient
import ai.dev.kit.providers.mlflow.getTraces
import ai.dev.kit.providers.mlflow.tracing.MlflowTracingTests
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import kotlin.test.Test

class TestDumbAutologTracingMlflow : TestDumbAutologTracingBase(
    ::getTraces,
    KotlinMlflowClient
), MlflowTracingTests

class TestDumbFluentTracingMlflow : TestDumbFluentTracingBase(
    ::getTraces,
    KotlinMlflowClient
), MlflowTracingTests

class TestDumbSuspendFluentTracingMlflow : TestDumbSuspendFluentTracingBase(
    ::getTraces,
    KotlinMlflowClient
), MlflowTracingTests

@KotlinFlowTrace(name = "Top Level Span")
internal fun topLevelTestFunction(x: String): String = withTrace(
    function = ::topLevelTestFunction,
    args = arrayOf<Any?>(x),
) {
    return@withTrace x.reversed()
}

class TopLevelFunctionTracingMlflow : MlflowTracingTests {
    @Test
    fun `test top level function`() = runBlocking {
        topLevelTestFunction("RandomString")

        val tracesResponse = getTraces(listOf(KotlinMlflowClient.currentExperimentId))

        Assertions.assertEquals(1, tracesResponse.traces.size)
        val trace = tracesResponse.traces.first()
        Assertions.assertNotNull(trace)
        Assertions.assertEquals(KotlinMlflowClient.currentExperimentId, trace.experimentId)
    }
}