package ai.jetbrains.tracy.ktor.extensions

import ai.jetbrains.tracy.core.http.protocol.Url
import io.ktor.http.ContentType
import io.ktor.http.URLBuilder
import ai.jetbrains.tracy.core.http.protocol.ContentType as TracyContentType
import io.ktor.http.Url as KtorUrl

fun URLBuilder.toProtocolUrl() = Url(protocol.name, host, pathSegments)

fun KtorUrl.toProtocolUrl() = Url(protocol.name, host, segments)

fun ContentType.toContentType(): TracyContentType = TracyContentType.parse(this.toString())