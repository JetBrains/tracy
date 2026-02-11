/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package ai.jetbrains.tracy.core.adapters.media

import ai.jetbrains.tracy.core.fluent.processor.addExceptionAttributes
import io.opentelemetry.api.trace.Span
import java.net.URL

private const val WARNING_URL_LENGTH_LIMIT = 256

/**
 * Sets base64-related attributes into the span, ensuring that [DataUrl]
 * contains the data in the base64-encoded format.
 *
 * @see UploadableMediaContentAttributeKeys
 */
internal fun Span.setDataUrlAttributes(
    dataUrl: DataUrl,
    field: String,
    index: Int,
) {
    if (!dataUrl.base64) {
        val str = dataUrl.asString()
        val trimmed = if (str.length < WARNING_URL_LENGTH_LIMIT) str
        else str.substring(0, WARNING_URL_LENGTH_LIMIT) + "..."
        addExceptionAttributes(
            IllegalArgumentException(
                "Expect base64 encoding for the data url, received '$trimmed'"
            )
        )
    }

    val keys = UploadableMediaContentAttributeKeys.forIndex(index)

    setAttribute(keys.type, SupportedMediaContentTypes.BASE64.type)
    setAttribute(keys.field, field)
    setAttribute(keys.contentType, dataUrl.mediaType)
    setAttribute(keys.data, dataUrl.data)
}

/**
 * Installs URL-related fields for the uploadable media content into the span
 *
 *
 * @see UploadableMediaContentAttributeKeys
 */
internal fun Span.setUrlAttributes(
    url: String,
    field: String,
    index: Int,
) {
    if (!url.isValidUrl()) {
        addExceptionAttributes(IllegalArgumentException("Expected a valid URL, received: $url"))
    }
    val keys = UploadableMediaContentAttributeKeys.forIndex(index)

    setAttribute(keys.type, SupportedMediaContentTypes.URL.type)
    setAttribute(keys.field, field)
    setAttribute(keys.url, url)
}

/**
 * Tries to parse the given string as [URL].
 *
 * @return `true` if parsing into [URL] succeeds, otherwise `false`.
 */
fun String.isValidUrl(): Boolean {
    return runCatching {
        URL(this)
        true
    }.getOrDefault(false)
}
