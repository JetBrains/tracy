package ai.dev.kit.providers.langfuse.tracing

import ai.dev.kit.fluent.TestAutologTracingBase
import ai.dev.kit.fluent.TestFluentTracingBase
import ai.dev.kit.fluent.TestSuspendFluentTracingBase
import ai.dev.kit.providers.langfuse.getAllTracesForProject
import org.junit.jupiter.api.Tag

//const val TEST_PROJECT_NAME = "ai-dev-kit-tracing-tests"
const val TEST_PROJECT_NAME = "test_project_1"


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
