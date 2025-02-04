package org.example.ai.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.example.ai.mlflow.getCurrentTimestamp
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.Constructor
import org.yaml.snakeyaml.nodes.Tag
import org.yaml.snakeyaml.representer.Representer

@Serializable
data class ModelData(
    @SerialName("run_id") val runId: String,
    @SerialName("artifact_path") val artifactPath: String,
    @SerialName("utc_time_created") val utcTimeCreated: String = getCurrentTimestamp().toString(),
    @SerialName("flavors") val flavors: Flavors,
    @SerialName("signature") val signature: Signature,
)

@Serializable
data class Flavors(
    @SerialName("openai") val openai: OpenAI
)

@Serializable
data class OpenAI(
    @SerialName("openai_version") val openaiVersion: String,
    @SerialName("data") val data: String,
    @SerialName("code") val code: String?
)

@Serializable
data class Signature(
    @SerialName("inputs") val inputs: String,
    @SerialName("outputs") val outputs: String,
    @SerialName("params") val params: String? = null
)

/**
 * Creates model YAML for MLflow model logging.
 *
 * Should be logged as MLmodel artifact.
 */
fun createModelYaml(modelData: ModelData): String {
    val options = DumperOptions().apply {
        defaultFlowStyle = DumperOptions.FlowStyle.BLOCK // Makes YAML human-readable (Block style)
        isAllowReadOnlyProperties = true
    }

    val representer = Representer(options)
    representer.propertyUtils.isSkipMissingProperties = true // Ignore missing properties if present
    representer.addClassTag(ModelData::class.java, Tag.MAP)

    val yaml = Yaml(Constructor(ModelData::class.java, LoaderOptions()), representer, options)
    return yaml.dump(modelData)
}

fun createModelJson(modelData: ModelData): String {
    return json.encodeToString(ModelData.serializer(), modelData)
}

private val json = Json { encodeDefaults = true }