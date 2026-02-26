/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.core.instrumentation

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class TracingSessionProviderTestProjectId {
    @Test
    fun `currentProjectId returns default value when not set`() {
        assertNull(TracingSessionProvider.currentProjectId)
    }

    @Test
    fun `currentProjectId is correctly set and retrieved using withProjectId`() = runTest {
        val expectedId = "test-project-id"
        withProjectId(expectedId) {
            assertEquals(expectedId, TracingSessionProvider.currentProjectId)
        }
        assertNull(TracingSessionProvider.currentProjectId)
    }

    @Test
    fun `currentProjectId is correctly set and retrieved using withProjectIdBlocking`() {
        val expectedId = "test-project-id-blocking"
        withProjectIdBlocking(expectedId) {
            assertEquals(expectedId, TracingSessionProvider.currentProjectId)
        }
        assertNull(TracingSessionProvider.currentProjectId)
    }

    @Test
    fun `currentProjectId is correctly set and retrieved in nested withProjectId calls`() = runTest {
        val outerId = "outer-project-id"
        val innerId = "inner-project-id"

        withProjectId(outerId) {
            assertEquals(outerId, TracingSessionProvider.currentProjectId)

            withProjectId(innerId) {
                assertEquals(innerId, TracingSessionProvider.currentProjectId)
            }

            assertEquals(outerId, TracingSessionProvider.currentProjectId)
        }

        assertNull(TracingSessionProvider.currentProjectId)
    }

    @Test
    fun `currentProjectId is correctly set and retrieved with multiple coroutines`() = runTest {
        val expectedId1 = "project-id-1"
        val expectedId2 = "project-id-2"

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

        assertNull(TracingSessionProvider.currentProjectId)
    }

    @Test
    fun `project ID is reset after exception in withProjectId`() = runTest {
        try {
            withProjectId("test-id") {
                throw RuntimeException("Simulated error")
            }
        } catch (_: RuntimeException) {
            // Expected
        }
        assertNull(TracingSessionProvider.currentProjectId)
    }

    @Test
    fun `currentProjectId is propagated even if you hijack the context`() = runTest {
        val projectId = "run"
        withProjectId(projectId) {
            val mainThread = Thread.currentThread().name
            withContext(Dispatchers.IO) {
                val distinctThread = Thread.currentThread().name
                assertNotEquals(mainThread, distinctThread)

                assertEquals(projectId, TracingSessionProvider.currentProjectId)
            }
        }
    }

    @Test
    fun `currentProjectId is correctly set and retrieved from a suspend function`() = runTest {
        val expectedId = "test-project-id-blocking"

        @Suppress("RedundantSuspendModifier")
        suspend fun mySuspendFunction() {
            assertEquals(expectedId, TracingSessionProvider.currentProjectId)
        }

        withProjectId(expectedId) {
            mySuspendFunction()
        }
        assertNull(TracingSessionProvider.currentProjectId)
    }
}

class TracingSessionProviderTestSessionId {
    @Test
    fun `currentSessionId returns default value when not set`() {
        assertNull(TracingSessionProvider.currentSessionId)
    }

    @Test
    fun `currentSessionId is correctly set and retrieved using withSessionId`() = runTest {
        val expectedId = "test-session-id"
        withSessionId(expectedId) {
            assertEquals(expectedId, TracingSessionProvider.currentSessionId)
        }
        assertNull(TracingSessionProvider.currentSessionId)
    }

    @Test
    fun `currentSessionId is correctly set and retrieved using withSessionIdBlocking`() {
        val expectedId = "test-run-id-blocking"
        withSessionIdBlocking(expectedId) {
            assertEquals(expectedId, TracingSessionProvider.currentSessionId)
        }
        assertNull(TracingSessionProvider.currentSessionId)
    }

    @Test
    fun `currentSessionId is correctly set and retrieved in nested withSessionId calls`() = runTest {
        val outerId = "outer-run-id"
        val innerId = "inner-run-id"

        withSessionId(outerId) {
            assertEquals(outerId, TracingSessionProvider.currentSessionId)

            withSessionId(innerId) {
                assertEquals(innerId, TracingSessionProvider.currentSessionId)
            }

            assertEquals(outerId, TracingSessionProvider.currentSessionId)
        }

        assertNull(TracingSessionProvider.currentSessionId)
    }

    @Test
    fun `currentSessionId is correctly set and retrieved with multiple coroutines`() = runTest {
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

        assertNull(TracingSessionProvider.currentSessionId)
    }

    @Test
    fun `run ID is reset after exception in withSessionId`() = runTest {
        try {
            withSessionId("test-id") {
                throw RuntimeException("Simulated error")
            }
        } catch (_: RuntimeException) {
            // Expected
        }
        assertNull(TracingSessionProvider.currentSessionId)
    }

    @Test
    fun `currentSessionId is propagated even if you hijack the context`() = runTest {
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

    @Test
    fun `currentSessionId is correctly set and retrieved from a suspend function`() = runTest {
        val expectedId = "test-run-id-suspend"

        @Suppress("RedundantSuspendModifier")
        suspend fun mySuspendFunction() {
            assertEquals(expectedId, TracingSessionProvider.currentProjectId)
        }

        withProjectId(expectedId) {
            mySuspendFunction()
        }
        assertNull(TracingSessionProvider.currentProjectId)
    }
}

class TracingSessionProviderTestUnsupportedScenarios {
    @Test
    fun `currentSessionId is not propagated if you fork a thread explicitly`() = runTest {
        withSessionId("This won't work! :(") {
            thread {
                // Session ID is not set
                assertNull(TracingSessionProvider.currentSessionId)
            }.join()
        }
    }

    @Test
    fun `currentProjectId is not propagated if you runBlocking within`() = runTest {
        withSessionId("This won't work! :(") {
            runBlocking {
                // Project ID is not set here
                assertNull(TracingSessionProvider.currentProjectId)
            }
        }
    }
}


