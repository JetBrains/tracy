/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package ai.jetbrains.tracy.core.adapters.media

import java.nio.charset.StandardCharsets

/**
 * Parts of the data URL.
 *
 * See details about data URLs at [MDN data URLs](https://developer.mozilla.org/en-US/docs/Web/URI/Reference/Schemes/data#syntax).
 *
 * **Example**:
 * 1. `data:text/plain;base64,SGVsbG8gV29ybGQ=`:
 * ```kotlin
 * DataUrl(
 *    mediaType = "text/plain",
 *    parameters = mapOf("charset" to "US-ASCII"),
 *    base64 = true,
 *    data = "SGVsbG8gV29ybGQ=",
 * )
 * ```
 */
data class DataUrl(
    val mediaType: String,
    val parameters: Map<String, List<String>>,
    val base64: Boolean,
    val data: String,
) {
    fun asString(): String {
        val parametersString = parameters.toMap().toList()
            .joinToString(separator = ";") { "${it.first}=${it.second.joinToString(separator = ",")}" }
                .let { if (it.isNotEmpty()) ";$it" else it }

        val base64String = if (base64) ";base64" else ""

        return "data:$mediaType$parametersString$base64String,$data"
    }

    companion object {
        /**
         * Parses an inline data URL extracting media type, parameters, and data.
         *
         * See [MDN data URLs](https://developer.mozilla.org/en-US/docs/Web/URI/Reference/Schemes/data)
         *
         * @see Resource.InlineDataUrl
         * @return DataUrl with the URL components, otherwise `null`
         */
        fun Resource.InlineDataUrl.parseInlineDataUrl(): DataUrl? {
            val url = this.inlineDataUrl
            if (!url.startsWith("data:")) {
                return null
            }

            // Pattern: data:[<media-type>][;<attribute>=<value>]*[;base64],<data>
            val dataUrlRegex = Regex(
                "^data:([^,;]*)((?:;[^,;=]+=[^,;]+)*)(;base64)?,(.*)$",
                RegexOption.DOT_MATCHES_ALL
            )

            val matchResult = dataUrlRegex.matchEntire(url) ?: return null

            val mediaTypeRaw = matchResult.groupValues[1].trim()
            val attributesRaw = matchResult.groupValues[2]
            val base64Marker = matchResult.groupValues[3]
            val data = matchResult.groupValues[4]

            // If media type omitted, it defaults to `text/plain;charset=US-ASCII` -> assign 'text/plain'
            val mediaType = if (mediaTypeRaw.isNotBlank()) mediaTypeRaw.trim() else "text/plain"

            val parameters = buildMap<String, MutableList<String>> {
                // If the media type is text/* (or defaulted to text/*),
                // the charset defaults to `charset=US-ASCII`.
                // See: https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/MIME_types#structure_of_a_mime_type
                if (mediaType.startsWith("text/") && !attributesRaw.contains("charset=")) {
                    put("charset", mutableListOf(StandardCharsets.US_ASCII.name()))
                }

                // insert other attributes
                if (attributesRaw.isNotEmpty()) {
                    // parse attributes (e.g., `;charset=UTF-8;foo=bar`)
                    val attributeRegex = Regex(";([^=]+)=([^;]+)")
                    val matches = attributeRegex.findAll(attributesRaw)
                    for (match in matches) {
                        val key = match.groupValues[1].trim()
                        val value = match.groupValues[2].trim()

                        if (!this.contains(key)) {
                            put(key, mutableListOf(value))
                            mutableListOf<String>()
                        } else {
                            this[key]?.add(value)
                        }
                    }
                }
            }

            val isBase64 = base64Marker.isNotEmpty()

            return DataUrl(
                mediaType = mediaType,
                parameters = parameters,
                base64 = isBase64,
                data = data
            )
        }
    }
}
