package ai.dev.kit.providers.mlflow.tracing

import ai.dev.kit.fluent.TestAutologTracingBase
import ai.dev.kit.fluent.TestFluentTracingBase
import ai.dev.kit.fluent.TestSuspendFluentTracingBase
import ai.dev.kit.providers.mlflow.getTraces
import ai.dev.kit.providers.mlflow.tracing.MlflowTracingTests.Companion.getExperimentId
import org.junit.jupiter.api.Tag

@Tag("SkipForNonLocal")
class TestAutologTracingMlflow : TestAutologTracingBase(
    ::getTraces,
    ::getExperimentId
), MlflowTracingTests

@Tag("SkipForNonLocal")
class TestFluentTracingMlflow : TestFluentTracingBase(
    ::getTraces,
    ::getExperimentId
), MlflowTracingTests

@Tag("SkipForNonLocal")
class TestSuspendFluentTracingMlflow : TestSuspendFluentTracingBase(
    ::getTraces,
    ::getExperimentId
), MlflowTracingTests
