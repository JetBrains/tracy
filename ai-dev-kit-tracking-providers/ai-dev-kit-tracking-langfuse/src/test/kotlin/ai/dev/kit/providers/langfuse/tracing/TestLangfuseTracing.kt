package ai.dev.kit.providers.langfuse.tracing

import ai.dev.kit.fluent.TestAutologTracingBase
import ai.dev.kit.fluent.TestFluentTracingBase
import ai.dev.kit.fluent.TestSuspendFluentTracingBase
import ai.dev.kit.providers.langfuse.KotlinLangfuseClient
import ai.dev.kit.providers.langfuse.getAllTracesForProject

class TestAutologTracingLangfuse : TestAutologTracingBase(
    ::getAllTracesForProject,
    KotlinLangfuseClient
), LangfuseTracingTests

class TestFluentTracingLangfuse : TestFluentTracingBase(
    ::getAllTracesForProject,
    KotlinLangfuseClient
), LangfuseTracingTests

class TestSuspendFluentTracingLangfuse : TestSuspendFluentTracingBase(
    ::getAllTracesForProject,
    KotlinLangfuseClient
), LangfuseTracingTests
