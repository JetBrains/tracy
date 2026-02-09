/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package ai.jetbrains.tracy.core.adapters.media

import io.opentelemetry.api.trace.Span

/**
 * Extracts media content (e.g., images, audio, files) from a JSON array
 * (likely, requests to/responses of LLM-specific APIs, e.g., OpenAI APIs)
 * and attaches it to the span under certain keys described by [UploadableMediaContentAttributeKeys].
 *
 * @see UploadableMediaContentAttributeKeys
 * @see Span.setUrlAttributes
 * @see Span.setDataUrlAttributes
 */
interface MediaContentExtractor {
    fun setUploadableContentAttributes(
        span: Span,
        field: String,
        content: MediaContent,
    )
}

/**
 * Represents a collection of media content parts.
 *
 * @property parts A list of `MediaContentPart` objects that constitute the media content.
 *
 * This class is typically used in conjunction with a [MediaContentExtractor] to attach extracted
 * media content to specific fields in spans for operations such as telemetry and tracing.
 *
 * @see MediaContentPart
 * @see MediaContentExtractor
 */
data class MediaContent(
    val parts: List<MediaContentPart>
)

/**
 * Represents a part of media content, consisting of a resource and an optional content type.
 *
 * This class is designed to handle various types of media resources, such as URLs, data URLs, or
 * Base64-encoded content. The resource defines the actual media data, while the content type
 * provides additional information about the format of the media.
 *
 * @property resource The media resource, which can be a URL, data URL, or Base64-encoded data.
 *
 * @see Resource
 * @see MediaContent
 */
data class MediaContentPart(val resource: Resource)

/**
 * Represents a sealed hierarchy of different resource types used to represent media content.
 *
 * This class is typically used to encapsulate various forms of resource data that can be processed
 * or transmitted, such as URLs, Data URLs, or Base64-encoded strings.
 *
 * Subclasses:
 * - Url: Represents a standard URL pointing to a resource.
 * - InlineDataUrl: Represents a Data URL containing inline data of the resource.
 * - Base64: Represents a Base64-encoded string containing the resource data.
 *
 * Use cases include:
 * - Storing references to external resources.
 * - Embedding data directly in the form of Base64 or Data URLs.
 */
sealed class Resource {
    data class Url(val url: String) : Resource()

    data class InlineDataUrl(val inlineDataUrl: String) : Resource()

    /**
     * @property base64 The Base64-encoded string containing the resource data.
     * @property mediaType The MIME type and optional parameters of the resource, which specifies the nature
     * of the resource data (e.g., `image/png`, `text/plain;charset=UTF-8`).
     */
    data class Base64(val base64: String, val mediaType: String) : Resource() {
        override fun toString(): String {
            return "Base64(mediaType=$mediaType, base64=${base64.take(10)}...)"
        }
    }
}