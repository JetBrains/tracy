package ai.jetbrains.tracy.core.adapters.media

import io.ktor.http.ContentType
import java.net.URL


/**
 * Tries to parse the given string as [URL].
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

fun ContentType.Companion.parseSafe(mimeType: String): ContentType? {
    return try {
        ContentType.parse(mimeType)
    } catch (_: Exception) {
        null
    }
}