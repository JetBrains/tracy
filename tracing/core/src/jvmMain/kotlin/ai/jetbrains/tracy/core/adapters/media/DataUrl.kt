/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package ai.jetbrains.tracy.core.adapters.media

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.nio.charset.StandardCharsets


// Pattern: data:[<media-type>][;<attribute>=<value>]*[;base64],<data>
private val DATA_URL_REGEX = Regex(
    "^data:([^,;]*)((?:;[^,;=]+=[^,;]+)*)(;base64)?,(.*)$",
    RegexOption.DOT_MATCHES_ALL
)

// Pattern: <media-type>[;<attribute>=<value>]
private val MEDIA_TYPE_REGEX = Regex(
    "^([^,;]*)((?:;[^,;=]+=[^,;]+)*)$",
    RegexOption.DOT_MATCHES_ALL
)

// Pattern: <attribute>=<value>
private val MEDIA_TYPE_ATTRIBUTE_REGEX = Regex(";([^=]+)=([^;]+)")


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
 *    parameters = mapOf("charset" to listOf("US-ASCII")),
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
        fun Resource.Base64.parseBase64(): DataUrl? {
            val matchResult = MEDIA_TYPE_REGEX.matchEntire(this.mediaType) ?: return null

            val mediaTypeRaw = matchResult.groupValues[1].trim()
            val attributesRaw = matchResult.groupValues[2]

            val (mediaType, parameters) = parseMediaTypeAndAttributes(mediaTypeRaw, attributesRaw)
                ?: return null

            return DataUrl(
                mediaType,
                parameters,
                base64 = true,
                data = this.base64,
            )
        }

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

            val matchResult = DATA_URL_REGEX.matchEntire(url) ?: return null

            val mediaTypeRaw = matchResult.groupValues[1].trim()
            val attributesRaw = matchResult.groupValues[2]
            val base64Marker = matchResult.groupValues[3]
            val data = matchResult.groupValues[4]

            val (mediaType, parameters) = parseMediaTypeAndAttributes(mediaTypeRaw, attributesRaw)
                ?: return null

            val isBase64 = base64Marker.isNotEmpty()

            return DataUrl(
                mediaType = mediaType,
                parameters = parameters,
                base64 = isBase64,
                data = data
            )
        }

        /**
         * Parses the given media type and its associated attributes into a structured format.
         *
         * If the media type is omitted or blank, it defaults to "text/plain".
         * Additionally, if the media type starts with "text/" and the attributes do not specify a charset,
         * the default charset is set to "US-ASCII" as per MIME type conventions.
         *
         * See [MDN MIME types](https://developer.mozilla.org/en-US/docs/Web/HTTP/Basics_of_HTTP/MIME_types)
         *
         * @param mediaTypeRaw A raw string representing the media type (e.g., "text/plain").
         * @param attributesRaw A raw string containing the media type's attributes in the format
         *                      ";key1=value1;key2=value2".
         *
         * @return A pair where the first element is the processed media type as a string,
         *         and the second element is a map of attributes, where each key is an attribute name
         *         and its associated value is a list of corresponding values. If the media type is invalid,
         *         returns `null`.
         */
        private fun parseMediaTypeAndAttributes(
            mediaTypeRaw: String,
            attributesRaw: String
        ): Pair<String, Map<String, List<String>>>? {
            // If media type omitted, it defaults to `text/plain;charset=US-ASCII` -> assign 'text/plain'
            val mediaType = if (mediaTypeRaw.isNotBlank()) {
                mediaTypeRaw.toMediaTypeOrNull()?.toString()
            } else {
                "text/plain"
            }

            if (mediaType == null) {
                return null
            }

            val parameters = buildMap<String, MutableList<String>> {
                /**
                 * See: https://www.rfc-editor.org/rfc/rfc2045
                 *
                 * Excerpt: "All media type values, subtype values, and parameter names as defined are case-insensitive.
                 *           However, parameter values are case-sensitive unless otherwise specified
                 *           for the specific parameter."
                 */
                // insert other attributes
                if (attributesRaw.isNotEmpty()) {
                    // parse attributes (e.g., `;charset=UTF-8;foo=bar`)
                    val matches = MEDIA_TYPE_ATTRIBUTE_REGEX.findAll(attributesRaw)
                    for (match in matches) {
                        val keyLowered = match.groupValues[1].trim().lowercase()
                        val value = match.groupValues[2].trim()

                        if (!this.contains(keyLowered)) {
                            put(keyLowered, mutableListOf(value))
                        } else {
                            this[keyLowered]?.add(value)
                        }
                    }
                }

                // If the charset is encountered in params, and the media type is `text/*` (or defaulted to `text/*`),
                // then the charset defaults to `charset=US-ASCII`:
                // See: https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/MIME_types#structure_of_a_mime_type
                if (!this.contains("charset") && mediaType.startsWith("text/")) {
                    put("charset", mutableListOf(StandardCharsets.US_ASCII.name()))
                }
            }

            return mediaType to parameters
        }
    }
}
