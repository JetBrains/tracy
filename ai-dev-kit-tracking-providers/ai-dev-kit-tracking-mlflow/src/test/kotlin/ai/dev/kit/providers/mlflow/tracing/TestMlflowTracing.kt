package ai.dev.kit.providers.mlflow.tracing

import ai.dev.kit.fluent.TestAutologTracingBase
import ai.dev.kit.fluent.TestFluentTracingBase
import ai.dev.kit.fluent.TestSuspendFluentTracingBase
import ai.dev.kit.providers.mlflow.KotlinMlflowClient
import ai.dev.kit.providers.mlflow.getTraces

class TestAutologTracingMlflow : TestAutologTracingBase(
    ::getTraces,
    KotlinMlflowClient
), MlflowTracingTests

class TestFluentTracingMlflow : TestFluentTracingBase(
    ::getTraces,
    KotlinMlflowClient
), MlflowTracingTests

class TestSuspendFluentTracingMlflow : TestSuspendFluentTracingBase(
    ::getTraces,
    KotlinMlflowClient
), MlflowTracingTests
