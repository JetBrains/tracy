/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.ktor

import org.jetbrains.ai.tracy.core.http.protocol.TracyContentType
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpResponse
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpResponseBody
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpUrl
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpUrlImpl
import org.jetbrains.ai.tracy.core.http.protocol.TracyQueryParameters
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.request
import io.ktor.http.URLBuilder
import io.ktor.client.statement.*
import io.ktor.http.*
import org.jetbrains.ai.tracy.core.http.protocol.*
import io.ktor.http.Url as KtorUrl

internal fun ContentType.toContentType(): TracyContentType {
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
    response: HttpResponse,
    override val body: TracyHttpResponseBody,
) : TracyHttpResponse {
    private val isError = response.status.isSuccess().not()

    override val contentType = response.contentType()?.toContentType()
    override val code = response.status.value
    override val url = response.request.url.toProtocolUrl()
    override val requestMethod = response.request.method.value.uppercase()

    override fun isError() = isError
}

internal fun URLBuilder.toProtocolUrl(): TracyHttpUrl {
    val builder = this

    val params = object : TracyQueryParameters {
        private val params = builder.parameters.build()
        override fun queryParameter(name: String): String? = params[name]
        override fun queryParameterValues(name: String) = params.getAll(name) ?: emptyList()
    }

    return TracyHttpUrlImpl(
        scheme = builder.protocol.name,
        host = builder.host,
        pathSegments = builder.pathSegments,
        parameters = params,
        url = builder.buildString(),
    )
}

internal fun KtorUrl.toProtocolUrl(): TracyHttpUrl {
    val url = this

    val params = object : TracyQueryParameters {
        override fun queryParameter(name: String) = url.parameters[name]
        override fun queryParameterValues(name: String) = url.parameters.getAll(name) ?: emptyList()
    }

    return TracyHttpUrlImpl(
        scheme = url.protocol.name,
        host = url.host,
        pathSegments = url.segments,
        parameters = params,
        url = url.toString(),
    )
}
