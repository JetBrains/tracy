package ai.dev.kit.providers.wandb.tracing.dumb

import ai.dev.kit.core.fluent.KotlinFlowTrace
import ai.dev.kit.core.fluent.processor.withTrace
import ai.dev.kit.fluent.TestDumbAutologTracingBase
import ai.dev.kit.fluent.TestDumbFluentTracingBase
import ai.dev.kit.fluent.TestDumbSuspendFluentTracingBase
import ai.dev.kit.providers.wandb.KotlinWandbClient
import ai.dev.kit.providers.wandb.WandbTracingTests
import ai.dev.kit.providers.wandb.getAllTracesForProject
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import kotlin.test.Test

class TestDumbAutologTracingWandb : TestDumbAutologTracingBase(
    ::getAllTracesForProject,
    KotlinWandbClient
), WandbTracingTests

class TestDumbFluentTracingWandb : TestDumbFluentTracingBase(
    ::getAllTracesForProject,
    KotlinWandbClient
), WandbTracingTests

class TestDumbSuspendFluentTracingWandb : TestDumbSuspendFluentTracingBase(
    ::getAllTracesForProject,
    KotlinWandbClient
), WandbTracingTests

@KotlinFlowTrace(name = "Top Level Span")
internal fun topLevelTestFunction(x: String): String = withTrace(
    function = ::topLevelTestFunction,
    args = arrayOf<Any?>(x),
) {
    return@withTrace x.reversed()
}

class TopLevelFunctionTracingWandb : WandbTracingTests {
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