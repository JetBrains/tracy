package ai.dev.kit.core.eval

data class Table(val columns: List<Column>) {
    init {
        check(columns.isNotEmpty()) { "Table must contain at least one column" }
        check(columns.map { it.data.size }.distinct().size == 1) { "All columns must contain the same number of rows"}
    }

    val columnNames: List<String> = columns.map { it.name }
    val numRows = columns.first().data.size
}

data class Column(val name: String, val data: List<Any?>)
fun Column.toTable() = Table(listOf(this))

fun tableOf(vararg columns: Column) = Table(columns.toList())
fun emptyTable() = Table(emptyList())

infix fun Table.join(other: Table): Table =
    Table(columns + other.columns)

infix fun Table.join(newColumn: Column): Table =
    Table(columns + newColumn)
