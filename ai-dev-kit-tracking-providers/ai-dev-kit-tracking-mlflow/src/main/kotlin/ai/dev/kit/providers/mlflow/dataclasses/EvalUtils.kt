package ai.dev.kit.providers.mlflow.dataclasses

import ai.dev.kit.eval.utils.Table
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class RunTag(
    val color: String
)

fun Table.dumpForMLFlow(): String = Json.encodeToString(MLFlowViewableTable.serializer(), toMLFlowViewableTable())

private fun Table.toMLFlowViewableTable(): MLFlowViewableTable =
    MLFlowViewableTable(
        columns = columnNames,
        data = (0 until numRows).map {
            rowIdx -> columns.map { it.data[rowIdx].toString() }
        }
    )

@Serializable
private data class MLFlowViewableTable(
    val columns: List<String>,
    val data: List<List<String>>
)
