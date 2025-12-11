package ai.dev.kit.common

import io.ktor.http.*
import io.ktor.util.*
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * Parses a data URL extracting media type, headers, and data.
 *
 * See [MDN data URLs](https://developer.mozilla.org/en-US/docs/Web/URI/Reference/Schemes/data)
 *
 * @see DataUrl
 * @return DataUrl with the URL components, otherwise `null`
 */
fun String.parseDataUrl(): DataUrl? {
    val url = this
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
        val headersString = headers.toMap().toList()
            .joinToString(separator = ";") {
                "${it.first}=${it.second.joinToString(separator = ",")}"
            }
            .let { if (it.isNotEmpty()) ";$it" else it }
        val base64String = if (base64) ";base64" else ""

        return "data:$mediaType$headersString$base64String,$data"
    }
}

fun Map<String, String>.toHeaders(): Headers {
    val headers = headers {
        for ((key, value) in entries) {
            set(key, value)
        }
    }
    return headers
}

/**
 * Tries to parse the given string as [java.net.URL].
 *
 * @return `true` if parsing into [URL] succeeds, otherwise `false`.
 */
fun String.isValidUrl(): Boolean {
    return try {
        URL(this)
        true
    } catch (_: Exception) {
        false
    }
}