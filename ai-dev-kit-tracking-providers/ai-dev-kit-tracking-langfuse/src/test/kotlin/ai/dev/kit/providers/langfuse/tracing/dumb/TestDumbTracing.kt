package ai.dev.kit.providers.langfuse.tracing.dumb

import ai.dev.kit.core.fluent.KotlinFlowTrace
import ai.dev.kit.core.fluent.processor.withTrace
import ai.dev.kit.fluent.TestDumbAutologTracingBase
import ai.dev.kit.fluent.TestDumbFluentTracingBase
import ai.dev.kit.fluent.TestDumbSuspendFluentTracingBase
import ai.dev.kit.providers.langfuse.KotlinLangfuseClient
import ai.dev.kit.providers.langfuse.LangfuseTracingTests
import ai.dev.kit.providers.langfuse.getAllTracesForProject
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import kotlin.test.Test

// These tests work only separately, because of ai.dev.kit.providers.langfuse.LoggingKt.getTraceIds. TODO: fix
class TestDumbAutologTracingLangfuse : TestDumbAutologTracingBase(
    ::getAllTracesForProject,
    KotlinLangfuseClient
), LangfuseTracingTests

class TestDumbFluentTracingLangfuse : TestDumbFluentTracingBase(
    ::getAllTracesForProject,
    KotlinLangfuseClient
), LangfuseTracingTests

class TestDumbSuspendFluentTracingLangfuse : TestDumbSuspendFluentTracingBase(
    ::getAllTracesForProject,
    KotlinLangfuseClient
), LangfuseTracingTests

@KotlinFlowTrace(name = "Top Level Span")
internal fun topLevelTestFunction(x: String): String = withTrace(
    function = ::topLevelTestFunction,
    args = arrayOf<Any?>(x),
) {
    return@withTrace x.reversed()
}

class TopLevelFunctionTracingLangfuse : LangfuseTracingTests {
    @Test
    fun `test top level function`() = runBlocking {
        topLevelTestFunction("RandomString")

        val tracesResponse = getAllTracesForProject(KotlinLangfuseClient.currentExperimentId)

        Assertions.assertEquals(1, tracesResponse.traces.size)
        val trace = tracesResponse.traces.first()
        Assertions.assertNotNull(trace)
        Assertions.assertEquals(KotlinLangfuseClient.currentExperimentId, trace.experimentId)
    }
}