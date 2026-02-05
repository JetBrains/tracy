/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package ai.jetbrains.tracy.core.adapters.media

import io.opentelemetry.api.common.AttributeKey

/**
 * OpenTelemetry attribute key generator for uploadable media content metadata.
 *
 * Generates indexed attribute keys for tracing media content (images, audio, documents) in spans.
 * Each media item gets a unique index, and this class provides type-safe access to its attributes.
 * Used internally by tracing adapters to attach media metadata to telemetry spans.
 *
 * ## Attribute Structure
 * Each media item has the following attributes:
 * - **type**: Encoding type ([SupportedMediaContentTypes.BASE64] or [SupportedMediaContentTypes.URL])
 * - **field**: Whether media is "input" or "output"
 * - **url**: External URL (for URL type)
 * - **contentType**: MIME type (e.g., "image/png", "audio/mp3")
 * - **data**: Base64-encoded data (for BASE64 type)
 *
 * ## Example
 * ```kotlin
 * val keys = UploadableMediaContentAttributeKeys.forIndex(0)
 * span.setAttribute(keys.type,
 *     SupportedMediaContentTypes.BASE64.type /* i.e., "base64" */)
 * span.setAttribute(keys.field, "input")
 * span.setAttribute(keys.contentType, "image/png")
 * span.setAttribute(keys.data, base64EncodedData)
 * // Results in attributes like:
 * // custom.uploadableMediaContent.0.type = "base64"
 * // custom.uploadableMediaContent.0.field = "input"
 * // custom.uploadableMediaContent.0.contentType = "image/png"
 * // custom.uploadableMediaContent.0.data = "iVBORw0KGg..."
 * ```
 *
 * @see setUrlAttributes
 * @see setDataUrlAttributes
 */
class UploadableMediaContentAttributeKeys private constructor(private val index: Int) {
    companion object {
        /** Prefix for all uploadable media content attribute keys */
        const val KEY_NAME_PREFIX = "custom.uploadableMediaContent"

        /**
         * Creates attribute keys for a specific media item index.
         * @param index Zero-based index of the media item in the request/response
         */
        fun forIndex(index: Int) = UploadableMediaContentAttributeKeys(index)
    }

    /** Attribute key for media encoding type (base64 or url) */
    val type: AttributeKey<String>
        get() = AttributeKey.stringKey("$KEY_NAME_PREFIX.$index.type")

    /** Attribute key for external URL reference */
    val url: AttributeKey<String>
        get() = AttributeKey.stringKey("$KEY_NAME_PREFIX.$index.url")

    /** Attribute key for MIME content type */
    val contentType: AttributeKey<String>
        get() = AttributeKey.stringKey("$KEY_NAME_PREFIX.$index.contentType")

    /** Attribute key for base64-encoded data */
    val data: AttributeKey<String>
        get() = AttributeKey.stringKey("$KEY_NAME_PREFIX.$index.data")

    /** Attribute key indicating whether media is "input" or "output" */
    val field: AttributeKey<String>
        get() = AttributeKey.stringKey("$KEY_NAME_PREFIX.$index.field")
}
