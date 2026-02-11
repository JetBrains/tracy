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
 */
interface Url {
    val scheme: String
    val host: String
    val pathSegments: List<String>
}

fun HttpUrl.toProtocolUrl(): Url {
    val httpUrl = this
    return object : Url {
        override val scheme = httpUrl.scheme
        override val host = httpUrl.host
        override val pathSegments = httpUrl.pathSegments
    }
}
