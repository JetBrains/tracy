package ai.dev.kit.providers.wandb.dataclasses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class Call(
    @SerialName("id") val id: String,
    @SerialName("project_id") val projectId: String,
    @SerialName("op_name") val opName: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("trace_id") val traceId: String,
    @SerialName("parent_id") val parentId: String? = null,
    @SerialName("started_at") val startedAt: String,
    @SerialName("ended_at") val endedAt: String,
    @SerialName("attributes") val attributes: Attributes,
    @SerialName("inputs") val inputs: JsonObject,
    @SerialName("output") val output: JsonElement,
    @SerialName("summary") val summary: Summary,
    @SerialName("wb_user_id") val wbUserId: String? = null,
    @SerialName("wb_run_id") val wbRunId: String? = null,
    @SerialName("exception") val exception: JsonElement? = null,
    @SerialName("deleted_at") val deletedAt: JsonElement? = null,
    @SerialName("storage_size_bytes") val storageSizeBytes: JsonElement? = null,
    @SerialName("total_storage_size_bytes") val totalStorageSizeBytes: JsonElement? = null
)

@Serializable
data class Summary(
    @SerialName("status") val status: String = "unknown",
)

@Serializable
data class Attributes(
    @SerialName("spanType") val spanType: String,
    @SerialName("spanSource") val spanSource: String? = null
)

@Serializable
data class DeleteCallsRequest(
    @SerialName("project_id") val projectId: String,
    @SerialName("call_ids") val callIds: List<String>
)
