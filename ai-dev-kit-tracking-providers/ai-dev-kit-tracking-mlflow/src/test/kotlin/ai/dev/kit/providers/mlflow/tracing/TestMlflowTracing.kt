package ai.dev.kit.providers.mlflow.tracing

import ai.dev.kit.fluent.TestAutologTracingBase
import ai.dev.kit.fluent.TestFluentTracingBase
import ai.dev.kit.fluent.TestSuspendFluentTracingBase
import ai.dev.kit.providers.mlflow.MlflowEvaluationClient
import ai.dev.kit.providers.mlflow.getTraces
import kotlinx.coroutines.runBlocking
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

private fun getExperimentId(): String = runBlocking {
    MlflowEvaluationClient.getOrCreateExperiment(experimentName = generateRandomString())
}

private fun generateRandomString(length: Int = 10): String {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    return (1..length).map { chars.random() }.joinToString("")
}
