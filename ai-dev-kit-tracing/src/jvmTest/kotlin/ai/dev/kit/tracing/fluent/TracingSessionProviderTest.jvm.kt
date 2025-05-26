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
        assertEquals(TracingSessionProvider.UNSET_EXPERIMENT_ID_TEXT, TracingSessionProvider.currentProjectId)
    }

    @Test
    fun `currentExperimentId is correctly set and retrieved using withExperimentId`() = runTest {
        val expectedId = "test-experiment-id"
        withProjectId(expectedId) {
            assertEquals(expectedId, TracingSessionProvider.currentProjectId)
        }
        assertEquals(TracingSessionProvider.UNSET_EXPERIMENT_ID_TEXT, TracingSessionProvider.currentProjectId)
    }

    @Test
    fun `currentExperimentId is correctly set and retrieved using withExperimentIdBlocking`() {
        val expectedId = "test-experiment-id-blocking"
        withProjectIdBlocking(expectedId) {
            assertEquals(expectedId, TracingSessionProvider.currentProjectId)
        }
        assertEquals(TracingSessionProvider.UNSET_EXPERIMENT_ID_TEXT, TracingSessionProvider.currentProjectId)
    }

    @Test
    fun `currentExperimentId is correctly set and retrieved in nested withExperimentId calls`() = runTest {
        val outerId = "outer-experiment-id"
        val innerId = "inner-experiment-id"

        withProjectId(outerId) {
            assertEquals(outerId, TracingSessionProvider.currentProjectId)

            withProjectId(innerId) {
                assertEquals(innerId, TracingSessionProvider.currentProjectId)
            }

            assertEquals(outerId, TracingSessionProvider.currentProjectId)
        }

        assertEquals(TracingSessionProvider.UNSET_EXPERIMENT_ID_TEXT, TracingSessionProvider.currentProjectId)
    }

    @Test
    fun `currentExperimentId is correctly set and retrieved with multiple coroutines`() = runTest {
        val expectedId1 = "experiment-id-1"
        val expectedId2 = "experiment-id-2"

        val job1 = launch(Dispatchers.Default) {
            withProjectId(expectedId1) {
                delay(30)
                assertEquals(expectedId1, TracingSessionProvider.currentProjectId)
            }
        }

        val job2 = launch(Dispatchers.Default) {
            withProjectId(expectedId2) {
                delay(40)
                assertEquals(expectedId2, TracingSessionProvider.currentProjectId)
            }
        }

        job1.join()
        job2.join()

        assertEquals(TracingSessionProvider.UNSET_EXPERIMENT_ID_TEXT, TracingSessionProvider.currentProjectId)
    }

    @Test
    fun `experiment ID is reset after exception in withExperimentId`() = runTest {
        try {
            withProjectId("test-id") {
                throw RuntimeException("Simulated error")
            }
        } catch (e: RuntimeException) {
            // Expected
        }
        assertEquals(TracingSessionProvider.UNSET_EXPERIMENT_ID_TEXT, TracingSessionProvider.currentProjectId)
    }

    @Test
    fun `currentExperimentId is propagated even if you hijack the context`() = runTest {
        val experimentId = "run"
        withProjectId(experimentId) {
            val mainThread = Thread.currentThread().name
            withContext(Dispatchers.IO) {
                val distinctThread = Thread.currentThread().name
                assertNotEquals(mainThread, distinctThread)

                assertEquals(experimentId, TracingSessionProvider.currentProjectId)
            }
        }
    }
}

class TracingSessionProviderTest_RunId {
    @Test
    fun `currentRunId returns default value when not set`() {
        assertEquals(TracingSessionProvider.UNSET_RUN_ID_TEXT, TracingSessionProvider.currentSessionId)
    }

    @Test
    fun `currentRunId is correctly set and retrieved using withRunId`() = runTest {
        val expectedId = "test-run-id"
        withSessionId(expectedId) {
            assertEquals(expectedId, TracingSessionProvider.currentSessionId)
        }
        assertEquals(TracingSessionProvider.UNSET_RUN_ID_TEXT, TracingSessionProvider.currentSessionId)
    }

    @Test
    fun `currentRunId is correctly set and retrieved using withRunIdBlocking`() {
        val expectedId = "test-run-id-blocking"
        withSessionIdBlocking(expectedId) {
            assertEquals(expectedId, TracingSessionProvider.currentSessionId)
        }
        assertEquals(TracingSessionProvider.UNSET_RUN_ID_TEXT, TracingSessionProvider.currentSessionId)
    }

    @Test
    fun `currentRunId is correctly set and retrieved in nested withRunId calls`() = runTest {
        val outerId = "outer-run-id"
        val innerId = "inner-run-id"

        withSessionId(outerId) {
            assertEquals(outerId, TracingSessionProvider.currentSessionId)

            withSessionId(innerId) {
                assertEquals(innerId, TracingSessionProvider.currentSessionId)
            }

            assertEquals(outerId, TracingSessionProvider.currentSessionId)
        }

        assertEquals(TracingSessionProvider.UNSET_RUN_ID_TEXT, TracingSessionProvider.currentSessionId)
    }

    @Test
    fun `currentRunId is correctly set and retrieved with multiple coroutines`() = runTest {
        val expectedId1 = "run-id-1"
        val expectedId2 = "run-id-2"

        val job1 = launch(Dispatchers.Default) {
            withSessionId(expectedId1) {
                delay(30)
                assertEquals(expectedId1, TracingSessionProvider.currentSessionId)
            }
        }

        val job2 = launch(Dispatchers.Default) {
            withSessionId(expectedId2) {
                delay(40)
                assertEquals(expectedId2, TracingSessionProvider.currentSessionId)
            }
        }

        job1.join()
        job2.join()

        assertEquals(TracingSessionProvider.UNSET_RUN_ID_TEXT, TracingSessionProvider.currentSessionId)
    }

    @Test
    fun `run ID is reset after exception in withRunId`() = runTest {
        try {
            withSessionId("test-id") {
                throw RuntimeException("Simulated error")
            }
        } catch (_: RuntimeException) {
            // Expected
        }
        assertEquals(TracingSessionProvider.UNSET_RUN_ID_TEXT, TracingSessionProvider.currentSessionId)
    }

    @Test
    fun `currentRunId is propagated even if you hijack the context`() = runTest {
        val runId = "run"
        withSessionId(runId) {
            val mainThread = Thread.currentThread().name
            withContext(Dispatchers.IO) {
                val distinctThread = Thread.currentThread().name
                assertNotEquals(mainThread, distinctThread)

                assertEquals(runId, TracingSessionProvider.currentSessionId)
            }
        }
    }
}

class TracingSessionProviderTest_UnsupportedScenarios {
    @Test
    fun `currentRunId is not propagated if you fork a thread explicitly`() = runTest {
        withSessionId("This won't work! :(") {
            thread {
                // Run ID is not set
                assertEquals(TracingSessionProvider.UNSET_RUN_ID_TEXT, TracingSessionProvider.currentSessionId)
            }.join()
        }
    }

    @Test
    fun `currentExperimentId is not propagated if you runBlocking within`() = runTest {
        withSessionId("This won't work! :(") {
            runBlocking {
                // Experiment ID is not set here
                assertEquals(
                    TracingSessionProvider.UNSET_EXPERIMENT_ID_TEXT,
                    TracingSessionProvider.currentProjectId
                )
            }
        }
    }
}


