/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package ai.jetbrains.tracy.core.http.protocol

import okhttp3.HttpUrl

/**
 * Represents a URL structure, defining its essential parts.
 *
 * @property scheme The scheme of the URL (e.g., "http", "https") representing the protocol.
 * @property host The host of the URL, indicating the domain or IP address.
 * @property pathSegments The path segments of the URL, representing
 *                        the hierarchical structure of the resource location.
 *
 * @see UrlImpl
 */
interface Url {
    val scheme: String
    val host: String
    val pathSegments: List<String>
}

/**
 * Direct implementation of [Url].
 *
 * Use it whenever you need to create an instance of [Url].
 */
data class UrlImpl(
    override val scheme: String,
    override val host: String,
    override val pathSegments: List<String>
) : Url

/**
 * Converts an instance of [HttpUrl] into a [Url] object by extracting its
 * scheme, host, and path segments, and constructing a new [UrlImpl] instance.
 *
 * @return A [Url] representation of the current [HttpUrl].
 */
fun HttpUrl.toProtocolUrl(): Url {
    val httpUrl = this
    return UrlImpl(
        scheme = httpUrl.scheme,
        host = httpUrl.host,
        pathSegments = httpUrl.pathSegments,
    )
}
