package ai.jetbrains.tracy.core.http.protocol

import ai.jetbrains.tracy.core.http.parsers.MultipartFormDataParser
import ai.jetbrains.tracy.core.http.parsers.FormData
import io.ktor.http.ContentType
import io.ktor.http.charset
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import mu.KotlinLogging
import okhttp3.MediaType

private val logger = KotlinLogging.logger {}

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
 * - [FormData]: Represents form-data typically used in multipart requests.
 */
sealed class RequestBody {
    data class Json(val json: JsonElement) : RequestBody()
    data class FormData(val data: ai.jetbrains.tracy.core.http.parsers.FormData) : RequestBody()
    object Empty : RequestBody()
}

fun RequestBody.asJson(): JsonElement? {
    return when (this) {
        is RequestBody.Json -> this.json
        else -> null
    }
}

fun RequestBody.asFormData(): FormData? {
    return when (this) {
        is RequestBody.FormData -> this.data
        else -> null
    }
}

/**
 * Converts a [ByteArray] into a [RequestBody] based on the provided [mediaType].
 *
 * This method interprets the byte array input as either JSON or multipart form data,
 * depending on the specified [mediaType]. If the [mediaType] is recognized as
 * `application/json`, the method attempts to parse the byte array into a JSON object.
 * For `multipart/form-data`, it parses the byte array into a form data structure.
 *
 * @param mediaType The media type of the data. Used to determine how to interpret the byte array.
 *                  Can be null if no content type is specified.
 * @return A [RequestBody] instance representing the parsed content, or null if
 *         the [mediaType] is unsupported or parsing fails.
 */
fun ByteArray.asRequestBody(mediaType: MediaType?): RequestBody? = mediaType?.toContentType()
    ?.let {
        asRequestBody(it)
    }

fun ByteArray.asRequestBody(contentType: ContentType): RequestBody? {
    val bytes = this
    // check for the content type regardless of the parameters
    return when (contentType.withoutParameters()) {
        ContentType.Application.Json -> {
            val json = try {
                Json.parseToJsonElement(
                    bytes.toString(contentType.charset() ?: Charsets.UTF_8)
                ).jsonObject
            } catch (err: Exception) {
                logger.trace("Error while parsing request body", err)
                null
            } ?: return null

            RequestBody.Json(json)
        }
        ContentType.MultiPart.FormData -> {
            val parser = MultipartFormDataParser()
            val formData = parser.parse(contentType, bytes)
            RequestBody.FormData(formData)
        }
        else -> null
    }
}

fun MediaType.toContentType(): ContentType = ContentType.parse(this.toString())
