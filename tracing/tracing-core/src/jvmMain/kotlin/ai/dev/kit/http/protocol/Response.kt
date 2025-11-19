package ai.dev.kit.http.protocol

import io.ktor.http.ContentType
import kotlinx.serialization.json.JsonElement


/**
 * Represents an HTTP response including its metadata and body content.
 *
 * @property contentType The content type of the HTTP response, specifying the media type of the body.
 *                       This value may be null if the content type is not specified.
 * @property code The HTTP status code of the response, indicating the result of the HTTP request
 *                (e.g., 200 for success, 404 for not found).
 * @property body The body of the HTTP response, encapsulated in a [ResponseBody] object, which can
 *                represent different response formats, such as JSON.
 */
data class Response(
    val contentType: ContentType?,
    val code: Int,
    val body: ResponseBody,
)

/**
 * Encapsulates the body content of an HTTP response.
 *
 * This sealed class is used as part of the [Response] data structure to represent the various
 * formats of data that can be included in the response body of an HTTP transaction.
 *
 * - [Json]: Represents a JSON response body containing structured data, which can be parsed
 *           and accessed as a [JsonElement].
 */
sealed class ResponseBody {
    data class Json(val json: JsonElement) : ResponseBody()
}

fun Response.isClientError(): Boolean {
    return this.code in 400..499
}

fun Response.isServerError(): Boolean {
    return this.code in 500..599
}

fun Response.isError() = isClientError() || isServerError()

fun ResponseBody.asJson(): JsonElement? {
    return when (this) {
        is ResponseBody.Json -> this.json
    }
}