package ai.jetbrains.tracy.core.http.protocol

import okhttp3.HttpUrl

data class Url(
    val scheme: String,
    val host: String,
    val pathSegments: List<String>,
)

fun HttpUrl.toProtocolUrl() = Url(scheme, host, pathSegments)
