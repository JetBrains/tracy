package ai.dev.kit.tracing.fluent

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class TracingSessionProviderTest_ExperimentId {
    @Test
    fun `currentExperimentId returns default value when not set`() {
        assertEquals(TracingSessionProvider.UNSET_EXPERIMENT_ID_TEXT, TracingSessionProvider.currentExperimentId)
    }

    @Test
    fun `currentExperimentId is correctly set and retrieved using withExperimentId`() = runTest {
        val expectedId = "test-experiment-id"
        withExperimentId(expectedId) {
            assertEquals(expectedId, TracingSessionProvider.currentExperimentId)
        }
        assertEquals(TracingSessionProvider.UNSET_EXPERIMENT_ID_TEXT, TracingSessionProvider.currentExperimentId)
    }

    @Test
    fun `currentExperimentId is correctly set and retrieved using withExperimentIdBlocking`() {
        val expectedId = "test-experiment-id-blocking"
        withExperimentIdBlocking(expectedId) {
            assertEquals(expectedId, TracingSessionProvider.currentExperimentId)
        }
        assertEquals(TracingSessionProvider.UNSET_EXPERIMENT_ID_TEXT, TracingSessionProvider.currentExperimentId)
    }

    @Test
    fun `currentExperimentId is correctly set and retrieved in nested withExperimentId calls`() = runTest {
        val outerId = "outer-experiment-id"
        val innerId = "inner-experiment-id"

        withExperimentId(outerId) {
            assertEquals(outerId, TracingSessionProvider.currentExperimentId)

            withExperimentId(innerId) {
                assertEquals(innerId, TracingSessionProvider.currentExperimentId)
            }

            assertEquals(outerId, TracingSessionProvider.currentExperimentId)
        }

        assertEquals(TracingSessionProvider.UNSET_EXPERIMENT_ID_TEXT, TracingSessionProvider.currentExperimentId)
    }

    @Test
    fun `currentExperimentId is correctly set and retrieved with multiple coroutines`() = runTest {
        val expectedId1 = "experiment-id-1"
        val expectedId2 = "experiment-id-2"

        val job1 = launch(Dispatchers.Default) {
            withExperimentId(expectedId1) {
                delay(30)
                assertEquals(expectedId1, TracingSessionProvider.currentExperimentId)
            }
        }

        val job2 = launch(Dispatchers.Default) {
            withExperimentId(expectedId2) {
                delay(40)
                assertEquals(expectedId2, TracingSessionProvider.currentExperimentId)
            }
        }

        job1.join()
        job2.join()

        assertEquals(TracingSessionProvider.UNSET_EXPERIMENT_ID_TEXT, TracingSessionProvider.currentExperimentId)
    }

    @Test
    fun `experiment ID is reset after exception in withExperimentId`() = runTest {
        try {
            withExperimentId("test-id") {
                throw RuntimeException("Simulated error")
            }
        } catch (e: RuntimeException) {
            // Expected
        }
        assertEquals(TracingSessionProvider.UNSET_EXPERIMENT_ID_TEXT, TracingSessionProvider.currentExperimentId)
    }

    @Test
    fun `currentExperimentId is propagated even if you hijack the context`() = runTest {
        val experimentId = "run"
        withExperimentId(experimentId) {
            val mainThread = Thread.currentThread().name
            withContext(Dispatchers.IO) {
                val distinctThread = Thread.currentThread().name
                assertNotEquals(mainThread, distinctThread)

                assertEquals(experimentId, TracingSessionProvider.currentExperimentId)
            }
        }
    }
}

class TracingSessionProviderTest_RunId {
    @Test
    fun `currentRunId returns default value when not set`() {
        assertEquals(TracingSessionProvider.UNSET_RUN_ID_TEXT, TracingSessionProvider.currentRunId)
    }

    @Test
    fun `currentRunId is correctly set and retrieved using withRunId`() = runTest {
        val expectedId = "test-run-id"
        withRunId(expectedId) {
            assertEquals(expectedId, TracingSessionProvider.currentRunId)
        }
        assertEquals(TracingSessionProvider.UNSET_RUN_ID_TEXT, TracingSessionProvider.currentRunId)
    }

    @Test
    fun `currentRunId is correctly set and retrieved using withRunIdBlocking`() {
        val expectedId = "test-run-id-blocking"
        withRunIdBlocking(expectedId) {
            assertEquals(expectedId, TracingSessionProvider.currentRunId)
        }
        assertEquals(TracingSessionProvider.UNSET_RUN_ID_TEXT, TracingSessionProvider.currentRunId)
    }

    @Test
    fun `currentRunId is correctly set and retrieved in nested withRunId calls`() = runTest {
        val outerId = "outer-run-id"
        val innerId = "inner-run-id"

        withRunId(outerId) {
            assertEquals(outerId, TracingSessionProvider.currentRunId)

            withRunId(innerId) {
                assertEquals(innerId, TracingSessionProvider.currentRunId)
            }

            assertEquals(outerId, TracingSessionProvider.currentRunId)
        }

        assertEquals(TracingSessionProvider.UNSET_RUN_ID_TEXT, TracingSessionProvider.currentRunId)
    }

    @Test
    fun `currentRunId is correctly set and retrieved with multiple coroutines`() = runTest {
        val expectedId1 = "run-id-1"
        val expectedId2 = "run-id-2"

        val job1 = launch(Dispatchers.Default) {
            withRunId(expectedId1) {
                delay(30)
                assertEquals(expectedId1, TracingSessionProvider.currentRunId)
            }
        }

        val job2 = launch(Dispatchers.Default) {
            withRunId(expectedId2) {
                delay(40)
                assertEquals(expectedId2, TracingSessionProvider.currentRunId)
            }
        }

        job1.join()
        job2.join()

        assertEquals(TracingSessionProvider.UNSET_RUN_ID_TEXT, TracingSessionProvider.currentRunId)
    }

    @Test
    fun `run ID is reset after exception in withRunId`() = runTest {
        try {
            withRunId("test-id") {
                throw RuntimeException("Simulated error")
            }
        } catch (_: RuntimeException) {
            // Expected
        }
        assertEquals(TracingSessionProvider.UNSET_RUN_ID_TEXT, TracingSessionProvider.currentRunId)
    }

    @Test
    fun `currentRunId is propagated even if you hijack the context`() = runTest {
        val runId = "run"
        withRunId(runId) {
            val mainThread = Thread.currentThread().name
            withContext(Dispatchers.IO) {
                val distinctThread = Thread.currentThread().name
                assertNotEquals(mainThread, distinctThread)

                assertEquals(runId, TracingSessionProvider.currentRunId)
            }
        }
    }
}

class TracingSessionProviderTest_UnsupportedScenarios {
    @Test
    fun `currentRunId is not propagated if you fork a thread explicitly`() = runTest {
        withRunId("This won't work! :(") {
            thread {
                // Run ID is not set
                assertEquals(TracingSessionProvider.UNSET_RUN_ID_TEXT, TracingSessionProvider.currentRunId)
            }.join()
        }
    }

    @Test
    fun `currentExperimentId is not propagated if you runBlocking within`() = runTest {
        withRunId("This won't work! :(") {
            runBlocking {
                // Experiment ID is not set here
                assertEquals(
                    TracingSessionProvider.UNSET_EXPERIMENT_ID_TEXT,
                    TracingSessionProvider.currentExperimentId
                )
            }
        }
    }
}


