package ai.dev.kit.http.protocol

import ai.dev.kit.http.parsers.FormData
import io.ktor.http.ContentType
import kotlinx.serialization.json.JsonElement
import okhttp3.MediaType


/**
 * Represents an HTTP request with its associated properties.
 *
 * @param url The URL to which the request is sent.
 *            This includes the scheme, host, and path segments.
 * @param contentType The content type of the request, indicating the type of data included in the body.
 *                    This may be null if not specified.
 * @param body The body of the request, containing the actual data to be sent.
 *             This can be represented as JSON or form data.
 */
data class Request(
    val url: Url,
    val contentType: ContentType?,
    val body: RequestBody,
)

/**
 * Represents the body content of an HTTP request. It can either be a JSON payload or form data.
 *
 * This sealed class is used as part of the [Request] data structure to encapsulate the various
 * types of data that can be transmitted as the body of an HTTP request.
 *
 * - [Json]: Represents a JSON body containing structured data.
 * - [DataForm]: Represents form-data typically used in multipart requests.
 */
sealed class RequestBody {
    data class Json(val json: JsonElement) : RequestBody()
    data class DataForm(val data: FormData) : RequestBody()
}

fun MediaType.toContentType(): ContentType = ContentType.parse(this.toString())

fun RequestBody.asJson(): JsonElement? {
    return when (this) {
        is RequestBody.Json -> this.json
        else -> null
    }
}

fun RequestBody.asFormData(): FormData? {
    return when (this) {
        is RequestBody.DataForm -> this.data
        else -> null
    }
}