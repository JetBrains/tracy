/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.core.http.protocol

import org.jetbrains.ai.tracy.core.InternalTracyApi
import okhttp3.HttpUrl

/**
 * Represents a URL structure, defining its essential parts.
 *
 * @property scheme The scheme of the URL (e.g., "http", "https") representing the protocol.
 * @property host The host of the URL, indicating the domain or IP address.
 * @property port The port of the URL (e.g., 443 for HTTPS, 80 for HTTP).
 * @property pathSegments The path segments of the URL, representing
 *                        the hierarchical structure of the resource location.
 * @property parameters The query parameters associated with the URL.
 *
 * @see TracyHttpUrlImpl
 */
@InternalTracyApi
interface TracyHttpUrl {
    val scheme: String
    val host: String
    val port: Int
    val pathSegments: List<String>
    val parameters: TracyQueryParameters
}

@InternalTracyApi
interface TracyQueryParameters {
    /**
     * Returns the first value of a query parameter with the given name, or null if not found.
     */
    fun queryParameter(name: String): String?

    /**
     * Returns a list of values of a query parameter with the given name, or an empty list if not found.
     *
     * In the following example, the value list of `b` will contain `null`:
     * 1. `http://host/?a=apple&b`
     */
    fun queryParameterValues(name: String): List<String?>
}

/**
 * Direct implementation of [TracyHttpUrl].
 *
 * Use it whenever you need to create an instance of [TracyHttpUrl].
 */
@InternalTracyApi
data class TracyHttpUrlImpl(
    override val scheme: String,
    override val host: String,
    override val port: Int,
    override val pathSegments: List<String>,
    override val parameters: TracyQueryParameters,
) : TracyHttpUrl

/**
 * Converts an instance of [HttpUrl] into a [TracyHttpUrl] object by extracting its
 * scheme, host, and path segments, and constructing a new [TracyHttpUrlImpl] instance.
 *
 * @return A [TracyHttpUrl] representation of the current [HttpUrl].
 */
@InternalTracyApi
fun HttpUrl.toProtocolUrl(): TracyHttpUrl {
    val httpUrl = this

    val params = object : TracyQueryParameters {
        override fun queryParameter(name: String) = httpUrl.queryParameter(name)
        override fun queryParameterValues(name: String) = httpUrl.queryParameterValues(name)
    }

    return TracyHttpUrlImpl(
        scheme = httpUrl.scheme,
        host = httpUrl.host,
        port = httpUrl.port,
        pathSegments = httpUrl.pathSegments,
        parameters = params,
    )
}
