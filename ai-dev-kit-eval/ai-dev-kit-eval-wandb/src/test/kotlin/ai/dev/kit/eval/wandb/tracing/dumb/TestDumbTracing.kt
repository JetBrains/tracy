package ai.dev.kit.eval.wandb.tracing.dumb

import ai.dev.kit.core.fluent.KotlinFlowTrace
import ai.dev.kit.core.fluent.processor.withTrace
import ai.dev.kit.eval.base.TestDumbAutologTracingBase
import ai.dev.kit.eval.base.TestDumbFluentTracingBase
import ai.dev.kit.eval.base.TestDumbSuspendFluentTracingBase
import ai.dev.kit.eval.wandb.KotlinWandbClient
import ai.dev.kit.eval.wandb.fluent.WandbTracingMetadataConfigurator
import ai.dev.kit.eval.wandb.getAllTracesForProject
import ai.dev.kit.eval.wandb.tracing.WandbTracingTests
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import kotlin.test.Test

class TestDumbAutologTracingWandb : TestDumbAutologTracingBase(
    WandbTracingMetadataConfigurator,
    ::getAllTracesForProject,
    KotlinWandbClient
), WandbTracingTests

class TestDumbFluentTracingWandb : TestDumbFluentTracingBase(
    WandbTracingMetadataConfigurator,
    ::getAllTracesForProject,
    KotlinWandbClient
), WandbTracingTests

class TestDumbSuspendFluentTracingWandb : TestDumbSuspendFluentTracingBase(
    WandbTracingMetadataConfigurator,
    ::getAllTracesForProject,
    KotlinWandbClient
), WandbTracingTests

@KotlinFlowTrace(name = "Top Level Span")
internal fun topLevelTestFunction(x: String): String = withTrace(
    function = ::topLevelTestFunction,
    args = arrayOf<Any?>(x),
    tracingMetadataConfigurator = WandbTracingMetadataConfigurator
) {
    return@withTrace x.reversed()
}

class TopLevelFunctionTracing : WandbTracingTests {
    @Test
    fun `test top level function`() = runBlocking {
        topLevelTestFunction("RandomString")

        val tracesResponse = getAllTracesForProject(listOf(KotlinWandbClient.currentExperimentId))

        Assertions.assertEquals(1, tracesResponse.traces.size)
        val trace = tracesResponse.traces.first()
        Assertions.assertNotNull(trace)
        Assertions.assertEquals(KotlinWandbClient.currentExperimentId, trace.experimentId)
    }
}