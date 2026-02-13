/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package ai.jetbrains.tracy.core.http.protocol

import ai.jetbrains.tracy.core.http.parsers.FormData
import ai.jetbrains.tracy.core.http.parsers.MultipartFormDataParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import mu.KotlinLogging
import java.nio.charset.Charset

private val logger = KotlinLogging.logger {}

/**
 * Represents an HTTP request with its associated properties.
 *
 * @param url The URL to which the request is sent.
 *            This includes the scheme, host, and path segments.
 * @param contentType The content type of the request, indicating the type of data included in the body.
 * @param body The body of the request, containing the actual data to be sent.
 *             This can be represented as JSON or form data.
 */
interface Request {
    val body: RequestBody
    val contentType: ContentType
    val url: Url
}

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
 * Converts a [ByteArray] into a [RequestBody] based on the provided [contentType].
 * The given [ByteArray] is decoded according to the specified [charset].
 *
 * This method interprets the byte array input as either JSON or multipart form data,
 * depending on the specified [contentType]. If the [contentType] is recognized as
 * `application/json`, the method attempts to parse the byte array into a JSON object.
 * For `multipart/form-data`, it parses the byte array into a form data structure.
 *
 * @param contentType The mime type of the data (e.g., `application/json`). Used to determine how to interpret the byte array.
 * @param charset The character encoding used to decode the byte array.
 *
 * @return A [RequestBody] instance representing the parsed content, or null if
 *         the [contentType] is unsupported or parsing fails.
 */
fun ByteArray.asRequestBody(contentType: ContentType, charset: Charset): RequestBody? {
    val bytes = this
    return when (contentType.mimeType) {
        ContentType.Application.Json.mimeType -> {
            val json = try {
                Json.parseToJsonElement(string = bytes.toString(charset)).jsonObject
            } catch (err: Exception) {
                logger.trace("Error while parsing request body", err)
                null
            } ?: return null

            RequestBody.Json(json)
        }
        ContentType.MultiPart.FormData.mimeType -> {
            val parser = MultipartFormDataParser()
            val formData = parser.parse(contentType, bytes)
            RequestBody.FormData(formData)
        }
        else -> null
    }
}

fun RequestBody.asRequestView(
    contentType: ContentType,
    url: Url
): Request {
    val requestBody = this
    return object : Request {
        override val body = requestBody
        override val contentType = contentType
        override val url = url
    }
}
