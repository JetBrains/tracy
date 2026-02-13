package ai.jetbrains.tracy.ktor

import ai.jetbrains.tracy.core.http.protocol.ContentType
import ai.jetbrains.tracy.core.http.protocol.Url
import io.ktor.http.charset

fun io.ktor.http.ContentType.toContentType(): ContentType {
    val contentType = this
    return object : ContentType {
        override val type = contentType.contentType
        override val subtype = contentType.contentSubtype
        override fun asString() = contentType.toString()
        override fun parameter(name: String) = contentType.parameter(name)
        override fun charset() = contentType.charset()
    }
}

internal data class UrlImpl(
    override val scheme: String,
    override val host: String,
    override val pathSegments: List<String>
) : Url