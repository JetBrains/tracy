/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package ai.jetbrains.tracy.core.http.protocol

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
 * @property url The URL associated with the HTTP response (i.e., where the initial request was made to).
 */
interface Response {
    val contentType: ContentType?
    val code: Int
    val body: ResponseBody
    val url: Url

    fun isClientError(): Boolean {
        return this.code in 400..499
    }

    fun isServerError(): Boolean {
        return this.code in 500..599
    }

    fun isError() = isClientError() || isServerError()

    companion object
}

/**
 * Defines the structure and behavior for representing content types.
 *
 * A content type is typically used to describe the media type of data, consisting of a `type` and `subtype` component.
 * Implementations of this interface encapsulate these components and provide a derived `mimeType` property that
 * combines the type and subtype into a standard MIME type format.
 *
 * Example usages include specifying content types for HTTP requests and responses, file handling, and data serialization formats.
 *
 * @property type Represents the primary type of the content, such as `text`, `application`, or `image`.
 * @property subtype Represents the specific subtype of the content within its primary type, such as `plain` or `json`.
 * @property mimeType Concatenates the `type` and `subtype` properties into a single MIME type string, formatted as `type/subtype`.
 *                   This provides a standardized representation of the content type.
 */
interface ContentType {
    val type: String
    val subtype: String

    /**
     * Represents the MIME type of the content, combining the type and subtype properties.
     *
     * The `mimeType` property generates a string in the format of `type/subtype`.
     * It is derived by concatenating the `type` and `subtype` properties.
     */
    val mimeType: String
        get() = "$type/$subtype"

    fun asString(): String
}

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

fun ResponseBody.asJson(): JsonElement? {
    return when (this) {
        is ResponseBody.Json -> this.json
    }
}