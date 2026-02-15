/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package ai.jetbrains.tracy.ktor

import ai.jetbrains.tracy.core.http.protocol.ContentType
import ai.jetbrains.tracy.core.http.protocol.Response
import ai.jetbrains.tracy.core.http.protocol.ResponseBody
import ai.jetbrains.tracy.core.http.protocol.Url
import ai.jetbrains.tracy.core.http.protocol.UrlImpl
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.request
import io.ktor.http.URLBuilder
import io.ktor.http.Url as KtorUrl
import io.ktor.http.charset
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonObject

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

internal class ResponseView(
    private val response: HttpResponse,
    body: JsonObject,
) : Response {
    override val contentType = response.contentType()?.toContentType()
    override val code = response.status.value
    override val body = ResponseBody.Json(body)
    override val url = response.request.url.toProtocolUrl()

    override fun isError() = response.status.isSuccess().not()
}

internal fun URLBuilder.toProtocolUrl(): Url {
    val builder = this
    return UrlImpl(
        scheme = builder.protocol.name,
        host = builder.host,
        pathSegments = builder.pathSegments,
    )
}

internal fun KtorUrl.toProtocolUrl(): Url {
    val url = this
    return UrlImpl(
        scheme = url.protocol.name,
        host = url.host,
        pathSegments = url.segments,
    )
}
