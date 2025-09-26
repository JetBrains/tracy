package ai.dev.kit.eval.providers.dataclasses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Tag(
    @SerialName("key") val key: String, @SerialName("value") val value: String
)

@Serializable
data class RequestMetadata(
    @SerialName("key") val key: String,
    @SerialName("value") val value: String
)

enum class RunStatus {
    RUNNING,
    SCHEDULED,
    FINISHED,
    FAILED,
    KILLED
}
