package ai.dev.kit.http.protocol

import io.ktor.http.URLBuilder
import io.ktor.http.Url as KtorUrl
import okhttp3.HttpUrl

data class Url(
    val scheme: String,
    val host: String,
    val pathSegments: List<String>,
)

fun HttpUrl.toProtocolUrl() = Url(scheme, host, pathSegments)

fun URLBuilder.toProtocolUrl() = Url(protocol.name, host, pathSegments)

fun KtorUrl.toProtocolUrl() = Url(protocol.name, host, segments)
