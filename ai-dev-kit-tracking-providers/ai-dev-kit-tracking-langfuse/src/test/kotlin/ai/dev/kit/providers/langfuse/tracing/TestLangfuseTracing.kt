package ai.dev.kit.providers.langfuse.tracing

import ai.dev.kit.fluent.TestAutologTracingBase
import ai.dev.kit.fluent.TestFluentTracingBase
import ai.dev.kit.fluent.TestSuspendFluentTracingBase
import ai.dev.kit.providers.langfuse.LangfuseEvaluationClient
import ai.dev.kit.providers.langfuse.getAllTracesForProject
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag

private const val TEST_PROJECT_NAME = "test_project_1"
private fun getExperimentId() = runBlocking { LangfuseEvaluationClient.getOrCreateExperiment(TEST_PROJECT_NAME) }



@Tag("SkipForNonLocal")
class TestAutologTracingLangfuse : TestAutologTracingBase(
    getTraces = ::getAllTracesForProject,
    getExperimentId = { TEST_PROJECT_NAME }
), LangfuseTracingTests

@Tag("SkipForNonLocal")
class TestFluentTracingLangfuse : TestFluentTracingBase(
    getTraces = ::getAllTracesForProject,
    getExperimentId = { TEST_PROJECT_NAME }
), LangfuseTracingTests

@Tag("SkipForNonLocal")
class TestSuspendFluentTracingLangfuse : TestSuspendFluentTracingBase(
    getTraces = ::getAllTracesForProject,
    getExperimentId = { TEST_PROJECT_NAME }
), LangfuseTracingTests
