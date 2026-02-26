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
 * @property pathSegments The path segments of the URL, representing
 *                        the hierarchical structure of the resource location.
 *
 * @see TracyHttpUrlImpl
 */
@InternalTracyApi
interface TracyHttpUrl {
    val scheme: String
    val host: String
    val pathSegments: List<String>
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
    override val pathSegments: List<String>
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
    return TracyHttpUrlImpl(
        scheme = httpUrl.scheme,
        host = httpUrl.host,
        pathSegments = httpUrl.pathSegments,
    )
}
