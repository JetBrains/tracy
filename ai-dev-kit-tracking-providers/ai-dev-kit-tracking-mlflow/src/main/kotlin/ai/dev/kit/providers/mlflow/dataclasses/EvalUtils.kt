package ai.dev.kit.providers.mlflow.dataclasses

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.rows

data class RunTag(
    val color: String
)

fun DataFrame<*>.dumpForMLFlow(): String = Json.encodeToString(
    serializer = MLFlowViewableTable.serializer(),
    value = MLFlowViewableTable(
        columns = columnNames(),
        data = rows().map { it.values().map { it.toString() } }
    )
)

@Serializable
private data class MLFlowViewableTable(
    val columns: List<String>,
    val data: List<List<String>>
)
