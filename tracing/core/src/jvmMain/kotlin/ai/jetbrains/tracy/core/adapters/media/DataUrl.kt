package ai.jetbrains.tracy.core.adapters.media

import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.headers
import io.ktor.util.toMap
import java.nio.charset.StandardCharsets

/**
 * Parts of the data URL.
 *
 * See details [here](https://developer.mozilla.org/en-US/docs/Web/URI/Reference/Schemes/data#syntax).
 */
data class DataUrl(
    val mediaType: String,
    val headers: Headers,
    val base64: Boolean,
    val data: String,
) {
    fun asString(): String {
        val headersString = headers.toMap().toList().joinToString(separator = ";") {
            "${it.first}=${it.second.joinToString(separator = ",")}"
        }.let { if (it.isNotEmpty()) ";$it" else it }
        val base64String = if (base64) ";base64" else ""

        return "data:$mediaType$headersString$base64String,$data"
    }

    companion object {
        /**
         * Parses an inline data URL extracting media type, headers, and data.
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

            // Default media type if not specified
            val mediaType = if (mediaTypeRaw.isEmpty()) {
                ContentType.Text.Plain
            } else {
                ContentType.parse(mediaTypeRaw)
            }

            // parse headers/attributes (e.g., ;charset=UTF-8)
            val headers = headers {
                // add default charset for text/plain if not specified
                if (mediaType == ContentType.Text.Plain && !attributesRaw.contains("charset=")) {
                    set("charset", StandardCharsets.US_ASCII.name())
                }

                if (attributesRaw.isNotEmpty()) {
                    // parse attributes (e.g., `;charset=UTF-8;foo=bar`)
                    val attributeRegex = Regex(";([^=]+)=([^;]+)")
                    attributeRegex.findAll(attributesRaw).forEach { match ->
                        val key = match.groupValues[1].trim()
                        val value = match.groupValues[2].trim()
                        set(key, value)
                    }
                }
            }

            val isBase64 = base64Marker.isNotEmpty()

            return DataUrl(
                mediaType = mediaType.toString(),
                headers = if (!headers.isEmpty()) headers else Headers.Empty,
                base64 = isBase64,
                data = data
            )
        }
    }
}