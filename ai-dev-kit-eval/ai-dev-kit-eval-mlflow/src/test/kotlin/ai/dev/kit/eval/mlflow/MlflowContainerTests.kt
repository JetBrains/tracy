package ai.dev.kit.eval.mlflow

import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.Ports
import ai.dev.kit.eval.mlflow.KotlinMlflowClient.MLFLOW_HOST
import ai.dev.kit.eval.mlflow.KotlinMlflowClient.MLFLOW_PORT
import ai.dev.kit.eval.mlflow.KotlinMlflowClient.MLFLOW_VERSION
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.mlflow.tracking.MlflowClient
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.collections.set

@Testcontainers
open class MlflowContainerTests {
    companion object {
        internal lateinit var mlflowClient: MlflowClient

        @Container
        val mlflowContainer: GenericContainer<*> = GenericContainer("ghcr.io/mlflow/mlflow:v$MLFLOW_VERSION")
            .withExposedPorts(MLFLOW_PORT)
            .withCommand("mlflow server --host $MLFLOW_HOST --port $MLFLOW_PORT")
            .waitingFor(Wait.forListeningPort())
            .withCreateContainerCmdModifier { cmd ->
                cmd.withHostConfig(
                    HostConfig().withPortBindings(
                        Ports.Binding.bindPort(MLFLOW_PORT).let { binding ->
                            Ports().apply { bindings[ExposedPort.tcp(MLFLOW_PORT)] = arrayOf(binding) }
                        }
                    )
                )
            }

        @BeforeAll
        @JvmStatic
        fun setup() {
            mlflowContainer.start()
            mlflowClient = MlflowClient("http://${MLFLOW_HOST}:${MLFLOW_PORT}")
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            mlflowContainer.stop()
            mlflowClient.close()
        }
    }
}
