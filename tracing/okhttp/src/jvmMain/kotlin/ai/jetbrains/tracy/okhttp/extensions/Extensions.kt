package ai.jetbrains.tracy.okhttp.extensions

import ai.jetbrains.tracy.core.http.protocol.Url
import okhttp3.HttpUrl

fun HttpUrl.toProtocolUrl() = Url(scheme, host, pathSegments)
