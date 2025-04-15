package ai.dev.kit.eval.mlflow.tracing.dumb

import ai.dev.kit.core.fluent.KotlinFlowTrace
import ai.dev.kit.core.fluent.processor.withTrace
import ai.dev.kit.eval.base.TestDumbAutologTracingBase
import ai.dev.kit.eval.base.TestDumbFluentTracingBase
import ai.dev.kit.eval.base.TestDumbSuspendFluentTracingBase
import ai.dev.kit.eval.mlflow.KotlinMlflowClient
import ai.dev.kit.eval.mlflow.fluent.MlflowTracingMetadataConfigurator
import ai.dev.kit.eval.mlflow.getTraces
import ai.dev.kit.eval.mlflow.tracing.MlflowTracingTests
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import kotlin.test.Test

class TestDumbAutologTracingMlflow : TestDumbAutologTracingBase(
    MlflowTracingMetadataConfigurator,
    ::getTraces,
    KotlinMlflowClient
), MlflowTracingTests

class TestDumbFluentTracingMlflow : TestDumbFluentTracingBase(
    MlflowTracingMetadataConfigurator,
    ::getTraces,
    KotlinMlflowClient
), MlflowTracingTests

class TestDumbSuspendFluentTracingMlflow : TestDumbSuspendFluentTracingBase(
    MlflowTracingMetadataConfigurator,
    ::getTraces,
    KotlinMlflowClient
), MlflowTracingTests

@KotlinFlowTrace(name = "Top Level Span")
internal fun topLevelTestFunction(x: String): String = withTrace(
    function = ::topLevelTestFunction,
    args = arrayOf<Any?>(x),
    tracingMetadataConfigurator = MlflowTracingMetadataConfigurator
) {
    return@withTrace x.reversed()
}

class TopLevelFunctionTracing : MlflowTracingTests {
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