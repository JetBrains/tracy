package ai.jetbrains.tracy.core.http.protocol

data class Url(
    val scheme: String,
    val host: String,
    val pathSegments: List<String>,
)
