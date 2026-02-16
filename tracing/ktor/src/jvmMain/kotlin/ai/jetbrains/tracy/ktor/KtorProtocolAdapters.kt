/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package ai.jetbrains.tracy.ktor

import ai.jetbrains.tracy.core.http.protocol.TracyContentType
import ai.jetbrains.tracy.core.http.protocol.TracyHttpResponse
import ai.jetbrains.tracy.core.http.protocol.TracyHttpResponseBody
import ai.jetbrains.tracy.core.http.protocol.TracyHttpUrl
import ai.jetbrains.tracy.core.http.protocol.TracyHttpUrlImpl
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.request
import io.ktor.http.URLBuilder
import io.ktor.http.Url as KtorUrl
import io.ktor.http.charset
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonObject

fun io.ktor.http.ContentType.toContentType(): TracyContentType {
    val contentType = this
    return object : TracyContentType {
        override val type = contentType.contentType
        override val subtype = contentType.contentSubtype
        override fun asString() = contentType.toString()
        override fun parameter(name: String) = contentType.parameter(name)
        override fun charset() = contentType.charset()
    }
}

internal class TracyHttpResponseView(
    private val response: HttpResponse,
    body: JsonObject,
) : TracyHttpResponse {
    override val contentType = response.contentType()?.toContentType()
    override val code = response.status.value
    override val body = TracyHttpResponseBody.Json(body)
    override val url = response.request.url.toProtocolUrl()

    override fun isError() = response.status.isSuccess().not()
}

internal fun URLBuilder.toProtocolUrl(): TracyHttpUrl {
    val builder = this
    return TracyHttpUrlImpl(
        scheme = builder.protocol.name,
        host = builder.host,
        pathSegments = builder.pathSegments,
    )
}

internal fun KtorUrl.toProtocolUrl(): TracyHttpUrl {
    val url = this
    return TracyHttpUrlImpl(
        scheme = url.protocol.name,
        host = url.host,
        pathSegments = url.segments,
    )
}
