<<<<<<<< HEAD:ai-dev-kit-test-base/src/testFixtures/kotlin/ai/dev/kit/fluent/TestDumbSuspendFluentTracingBase.kt
package ai.dev.kit.fluent

import ai.dev.kit.core.fluent.KotlinFlowTrace
import ai.dev.kit.core.fluent.KotlinLoggingClient
import ai.dev.kit.core.fluent.dataclasses.TracesResponse
import ai.dev.kit.core.fluent.processor.withTrace
import ai.dev.kit.core.fluent.processor.withTraceSuspended
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import org.junit.jupiter.api.Test
import kotlin.reflect.KSuspendFunction1
import kotlin.test.assertContains
========
package ai.dev.kit.providers.mlflow.tracing

import ai.dev.kit.core.fluent.KotlinFlowTrace
import ai.dev.kit.core.fluent.processor.withTraceSuspended
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import ai.dev.kit.providers.mlflow.KotlinMlflowClient
import ai.dev.kit.providers.mlflow.getTraces
import kotlin.test.Test
>>>>>>>> bc74f14 (Support KCP plugin for tracing):ai-dev-kit-tracking-providers/ai-dev-kit-tracking-mlflow/src/test/kotlin/ai/dev/kit/providers/mlflow/tracing/TestSuspendFluentTracing.kt
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

internal class MyTestClassWithSuspend {
    @KotlinFlowTrace(name = "Main Span", spanType = "mySpanType")
    suspend fun testFunction(paramName: Int): Int {
        delay(12)
        return paramName
    }

    @KotlinFlowTrace(name = "Secondary Span", spanType = "func")
    suspend fun anotherTestFunction(x: String): String {
        delay(45)
        return x.reversed()
    }

    @KotlinFlowTrace(name = "Parent Span")
    suspend fun parentTestFunction(x: String): String {
        delay(50)
        return childTestFunction(x.reversed())
    }

    @KotlinFlowTrace(name = "Child Span")
    suspend fun childTestFunction(x: String): String {
        delay(10)
        val result = x.reversed()
        return result
    }

    @KotlinFlowTrace(name = "Parent Span Non Suspend")
    suspend fun parentTestFunctionWithNonSuspendKid(x: String): String {
        delay(50)
        return childTestFunctionNonSuspend(x.reversed())
    }

    @KotlinFlowTrace(name = "Child Span Non Suspend")
    fun childTestFunctionNonSuspend(x: String): String {
        return x.reversed()
    }

    @KotlinFlowTrace(name = "Parent Span Non Suspend")
    fun parentTestFunctionWithSuspendKid(x: String): String {
        return runBlocking { childTestFunctionSuspend(x.reversed()) }
    }

    @KotlinFlowTrace(name = "Child Span Non Suspend")
    suspend fun childTestFunctionSuspend(x: String): String {
        delay(10)
        return x.reversed()
    }

    @KotlinFlowTrace(name = "Child Span")
    suspend fun testRecursion(level: Int): Int {
        delay(100)
        if (level == 0) return 0
        return testRecursion(level - 1)
    }
}

<<<<<<<< HEAD:ai-dev-kit-test-base/src/testFixtures/kotlin/ai/dev/kit/fluent/TestDumbSuspendFluentTracingBase.kt
internal class MyTestClassWithSuspendDumbHard() {
    @KotlinFlowTrace(name = "P", spanType = "P")
    suspend fun parentFunction(p: String): String = withTraceSuspended(
========
internal class MyTestClassWithSuspendHard {
    @KotlinFlowTrace(name = "Parent Span")
    suspend fun parentFunction(param: String): String = withTraceSuspended(
>>>>>>>> bc74f14 (Support KCP plugin for tracing):ai-dev-kit-tracking-providers/ai-dev-kit-tracking-mlflow/src/test/kotlin/ai/dev/kit/providers/mlflow/tracing/TestSuspendFluentTracing.kt
        function = ::parentFunction,
        args = arrayOf<Any?>(p),
    ) {
        delay(100)
        // Calling Children
        val child1Result = childFunction1(p)
        val child2Result = childFunction2(p)
        val child3Result = childFunction3(p)

        return@withTraceSuspended "$child1Result, $child2Result, $child3Result"
    }

<<<<<<<< HEAD:ai-dev-kit-test-base/src/testFixtures/kotlin/ai/dev/kit/fluent/TestDumbSuspendFluentTracingBase.kt
    @KotlinFlowTrace(name = "C1", spanType = "C")
    suspend fun childFunction1(p: String): String = withTraceSuspended(
        function = ::childFunction1,
        args = arrayOf<Any?>(p),
    ) {
        delay(50)
        return@withTraceSuspended p.uppercase()
    }

    @KotlinFlowTrace(name = "C2", spanType = "C")
    suspend fun childFunction2(p: String): String = withTraceSuspended(
        function = ::childFunction2,
        args = arrayOf<Any?>(p),
    ) {
        delay(50)
        val grandChild1Result = grandChildFunction1(p)
        return@withTraceSuspended grandChild1Result
    }

    @KotlinFlowTrace(name = "C3", spanType = "C")
    suspend fun childFunction3(p: String): String = withTraceSuspended(
        function = ::childFunction3,
        args = arrayOf<Any?>(p),
    ) {
        delay(50)
        return@withTraceSuspended p.reversed()
    }

    @KotlinFlowTrace(name = "G1", spanType = "G")
    suspend fun grandChildFunction1(p: String): String = withTraceSuspended(
        function = ::grandChildFunction1,
        args = arrayOf<Any?>(p),
    ) {
        delay(30)
        return@withTraceSuspended "G(${p.length})"
    }
}

open class TestDumbSuspendFluentTracingBase(
    val getTraces: KSuspendFunction1<List<String>, TracesResponse>,
    private val client: KotlinLoggingClient
) {
    @Test
    fun `test trace creation`() = runBlocking {
        MyTestClassWithSuspendDumb().testFunction(1)
        val tracesResponse = getTraces(listOf(client.currentExperimentId))
========
    @KotlinFlowTrace(name = "Child1 Span")
    suspend fun childFunction1(param: String): String {
        delay(50)
        return param.uppercase()
    }

    @KotlinFlowTrace(name = "Child2 Span")
    suspend fun childFunction2(param: String): String {
        delay(50)
        val grandChild1Result = grandChildFunction1(param)
        val grandChild2Result = grandChildFunction2(param)
        return "$grandChild1Result and $grandChild2Result"
    }

    @KotlinFlowTrace(name = "Child3 Span")
    suspend fun childFunction3(param: String): String {
        delay(50)
        return param.reversed()
    }

    @KotlinFlowTrace(name = "GrandChild1 Span")
    suspend fun grandChildFunction1(param: String): String {
        delay(30)
        return "GrandChild1(${param.length})"
    }

    @KotlinFlowTrace(name = "GrandChild2 Span")
    suspend fun grandChildFunction2(param: String): String {
        delay(30)
        return "GrandChild2(${param.reversed()})"
    }
}


@KotlinFlowTrace(name = "Top Level Span")
internal fun topLevelTestFunction(x: String): String {
    return x.reversed()
}

class TestSuspendFluentTracing: MlflowTracingTests() {
    @Test
    fun `test trace creation`() = runBlocking {
        MyTestClassWithSuspend().testFunction(1)
        val tracesResponse = getTraces(listOf(KotlinMlflowClient.currentExperimentId))
>>>>>>>> bc74f14 (Support KCP plugin for tracing):ai-dev-kit-tracking-providers/ai-dev-kit-tracking-mlflow/src/test/kotlin/ai/dev/kit/providers/mlflow/tracing/TestSuspendFluentTracing.kt

        assertEquals(1, tracesResponse.traces.size)
        val trace = tracesResponse.traces.first()
        assertNotNull(trace)
        assertEquals(client.currentExperimentId, trace.experimentId)
    }

    @Test
<<<<<<<< HEAD:ai-dev-kit-test-base/src/testFixtures/kotlin/ai/dev/kit/fluent/TestDumbSuspendFluentTracingBase.kt
    fun `test trace tags and metadata are correct`() = runBlocking {
        val testClass = MyTestClassWithSuspendDumb()
========
    fun `test trace tags and metadata are correct`()  = runBlocking {
        val testClass = MyTestClassWithSuspend()
>>>>>>>> bc74f14 (Support KCP plugin for tracing):ai-dev-kit-tracking-providers/ai-dev-kit-tracking-mlflow/src/test/kotlin/ai/dev/kit/providers/mlflow/tracing/TestSuspendFluentTracing.kt
        val arg = 3
        val result = testClass.testFunction(arg)

        val tracesResponse = getTraces(listOf(client.currentExperimentId))
        var trace = tracesResponse.traces.firstOrNull()
        trace = assertNotNull(trace)

        assertEquals("OK", trace.status)
        assertEquals(
            "{\"paramName\":$arg}",
            trace.requestMetadata.firstOrNull { it.key == "traceInputs" }?.value ?: ""
        )
        assertEquals(
            result.toString(),
            trace.requestMetadata.firstOrNull { it.key == "traceOutputs" }?.value ?: ""
        )
        assertEquals(
            "Main Span",
            trace.tags.firstOrNull { it.key == "traceName" }?.value ?: ""
        )
        assertEquals(
            "[{\"name\":\"Main Span\",\"type\":\"mySpanType\",\"inputs\":\"{\\\"paramName\\\":$arg}\"}]",
            trace.tags.firstOrNull { it.key == "traceSpans" }?.value ?: ""
        )
    }

    @Test
    fun `test multiple trace creation`() = runBlocking {
        val testClass = MyTestClassWithSuspend()
        testClass.testFunction(1)
        testClass.anotherTestFunction("OpenTelemetry")

        val tracesResponse = getTraces(listOf(client.currentExperimentId))

        assertEquals(2, tracesResponse.traces.size)

        val firstTrace = tracesResponse.traces[0]
        val secondTrace = tracesResponse.traces[1]

        assertNotEquals(firstTrace.requestId, secondTrace.requestId, "Trace IDs should be unique.")
    }

    @Test
    fun `test parent child trace`() = runBlocking {
        MyTestClassWithSuspend().parentTestFunction("RandomString")

        val tracesResponse = getTraces(listOf(client.currentExperimentId))

        var trace = tracesResponse.traces.firstOrNull()
        trace = assertNotNull(trace)

        assertEquals(
            "[{\"name\":\"Child Span\",\"type\":\"UNKNOWN\",\"inputs\":\"{\\\"x\\\":\\\"gnirtSmodnaR\\\"}\"},{\"name\":\"Parent Span\",\"type\":\"UNKNOWN\",\"inputs\":\"{\\\"x\\\":\\\"RandomString\\\"}\"}]",
            trace.tags.firstOrNull { it.key == "traceSpans" }?.value ?: ""
        )
    }

    @Test
    fun `test parent child trace with non suspend child`() = runBlocking {
        MyTestClassWithSuspend().parentTestFunctionWithNonSuspendKid("RandomString")

        val tracesResponse = getTraces(listOf(client.currentExperimentId))

        var trace = tracesResponse.traces.firstOrNull()
        trace = assertNotNull(trace)

        assertEquals(
            "[{\"name\":\"Child Span Non Suspend\",\"type\":\"UNKNOWN\",\"inputs\":\"{\\\"x\\\":\\\"gnirtSmodnaR\\\"}\"},{\"name\":\"Parent Span Non Suspend\",\"type\":\"UNKNOWN\",\"inputs\":\"{\\\"x\\\":\\\"RandomString\\\"}\"}]",
            trace.tags.firstOrNull { it.key == "traceSpans" }?.value ?: ""
        )
    }

    @Test
    fun `test parent child trace with non suspend parent`() = runBlocking {
        MyTestClassWithSuspend().parentTestFunctionWithSuspendKid("RandomString")

        val tracesResponse = getTraces(listOf(client.currentExperimentId))
        var trace = tracesResponse.traces.firstOrNull()
        trace = assertNotNull(trace)

        assertEquals(
            "[{\"name\":\"Child Span Non Suspend\",\"type\":\"UNKNOWN\",\"inputs\":\"{\\\"x\\\":\\\"gnirtSmodnaR\\\"}\"},{\"name\":\"Parent Span Non Suspend\",\"type\":\"UNKNOWN\",\"inputs\":\"{\\\"x\\\":\\\"RandomString\\\"}\"}]",
            trace.tags.firstOrNull { it.key == "traceSpans" }?.value ?: ""
        )
    }

    @Test
    fun `test recursion`() = runBlocking {
        MyTestClassWithSuspend().testRecursion(2)

        val tracesResponse = getTraces(listOf(client.currentExperimentId))
        var trace = tracesResponse.traces.firstOrNull()
        trace = assertNotNull(trace)

        assertEquals(
            "[{\"name\":\"Child Span\",\"type\":\"UNKNOWN\",\"inputs\":\"{\\\"level\\\":0}\"},{\"name\":\"Child Span\",\"type\":\"UNKNOWN\",\"inputs\":\"{\\\"level\\\":1}\"},{\"name\":\"Child Span\",\"type\":\"UNKNOWN\",\"inputs\":\"{\\\"level\\\":2}\"}]",
            trace.tags.firstOrNull { it.key == "traceSpans" }?.value ?: ""
        )
    }

    @Test
    fun `test parent and child trace hierarchy`() = runBlocking {
<<<<<<<< HEAD:ai-dev-kit-test-base/src/testFixtures/kotlin/ai/dev/kit/fluent/TestDumbSuspendFluentTracingBase.kt
        val result = MyTestClassWithSuspendDumbHard().parentFunction("a")
========
        val result = MyTestClassWithSuspendHard().parentFunction("TestInput")
>>>>>>>> bc74f14 (Support KCP plugin for tracing):ai-dev-kit-tracking-providers/ai-dev-kit-tracking-mlflow/src/test/kotlin/ai/dev/kit/providers/mlflow/tracing/TestSuspendFluentTracing.kt

        val tracesResponse = getTraces(listOf(client.currentExperimentId))

        assertNotNull(tracesResponse)
        assertEquals(1, tracesResponse.traces.size)
        val trace = tracesResponse.traces.first()
        assertNotNull(trace)

        val expectedSpans = listOf(
            "{\"name\":\"C1\",\"type\":\"C\",\"inputs\":\"{\\\"p\\\":\\\"a\\\"}\"}",
            "{\"name\":\"G1\",\"type\":\"G\",\"inputs\":\"{\\\"p\\\":\\\"a\\\"}\"}",
            "{\"name\":\"G2\",\"type\":\"G\",\"inputs\":\"{\\\"p\\\":\\\"a\\\"}\"}",
            "{\"name\":\"C2\",\"type\":\"C\",\"inputs\":\"{\\\"p\\\":\\\"a\\\"}\"}",
            "{\"name\":\"C3\",\"type\":\"C\",\"inputs\":\"{\\\"p\\\":\\\"a\\\"}\"}",
            "{\"name\":\"P\",\"type\":\"P\",\"inputs\":\"{\\\"p\\\":\\\"a\\\"}\"}"
        )

        val spans = assertNotNull(trace.tags.firstOrNull { it.key == "traceSpans" }?.value)
        val actualSpans = Json.parseToJsonElement(spans).jsonArray

        actualSpans.forEach {
            // order of children/grandchildren is not guaranteed
            assertContains(expectedSpans, it.toString())
        }

        val expectedResult = "A, G(1), a"
        assertEquals(expectedResult, result)
    }

}
