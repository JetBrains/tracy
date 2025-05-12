package ai.dev.kit.providers.langfuse.tracing

import ai.dev.kit.fluent.TestAutologTracingBase
import ai.dev.kit.fluent.TestFluentTracingBase
import ai.dev.kit.fluent.TestSuspendFluentTracingBase
import ai.dev.kit.providers.langfuse.KotlinLangfuseClient
import ai.dev.kit.providers.langfuse.getAllTracesForProject
import org.junit.jupiter.api.Tag

@Tag("SkipForNonLocal")
class TestAutologTracingLangfuse : TestAutologTracingBase(
    ::getAllTracesForProject,
    KotlinLangfuseClient
), LangfuseTracingTests

@Tag("SkipForNonLocal")
class TestFluentTracingLangfuse : TestFluentTracingBase(
    ::getAllTracesForProject,
    KotlinLangfuseClient
), LangfuseTracingTests

@Tag("SkipForNonLocal")
class TestSuspendFluentTracingLangfuse : TestSuspendFluentTracingBase(
    ::getAllTracesForProject,
    KotlinLangfuseClient
), LangfuseTracingTests
