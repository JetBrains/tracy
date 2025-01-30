package ai.mlflow.logging

import kotlinx.coroutines.runBlocking
import org.example.ai.mlflow.RunStatus
import org.example.ai.mlflow.createRun
import org.example.ai.mlflow.getExperiment
import org.example.ai.mlflow.updateRun
import org.junit.jupiter.api.Test

class TestLogging {
    @Test
    fun testCreateRun() {
        runBlocking { createRun("test") }
    }

    @Test
    fun testUpdateRun() {
        runBlocking {
            val run = createRun("test")
            val runId = run.run.info.runId
            updateRun(runId, RunStatus.FINISHED)
        }
    }

    @Test
    fun testGetExperiment() {
        runBlocking { getExperiment() }
    }
}