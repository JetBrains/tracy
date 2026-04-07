/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.core.http.protocol

import org.jetbrains.ai.tracy.core.InternalTracyApi
import kotlinx.serialization.json.JsonElement

/**
 * Represents an HTTP response including its metadata and body content.
 *
 * @property contentType The content type of the HTTP response, specifying the media type of the body.
 *                       This value may be null if the content type is not specified.
 * @property code The HTTP status code of the response, indicating the result of the HTTP request
 *                (e.g., 200 for success, 404 for not found).
 * @property body The body of the HTTP response, encapsulated in a [TracyHttpResponseBody] object, which can
 *                represent different response formats, such as JSON.
 * @property url The URL associated with the HTTP response (i.e., where the initial request was made to).
 * @property requestMethod The HTTP method used by the request, which this response corresponds to.
 */
@InternalTracyApi
interface TracyHttpResponse {
    val contentType: TracyContentType?
    val code: Int
    val body: TracyHttpResponseBody
    val url: TracyHttpUrl
    val requestMethod: String

    fun isError(): Boolean
}

/**
 * Encapsulates the body content of an HTTP response.
 *
 * This sealed class is used as part of the [TracyHttpResponse] data structure to represent the various
 * formats of data that can be included in the response body of an HTTP transaction.
 *
 * - [Json]: Represents a JSON response body containing structured data, which can be parsed
 *           and accessed as a [JsonElement].
 */
@InternalTracyApi
sealed class TracyHttpResponseBody {
    data class Json(val json: JsonElement) : TracyHttpResponseBody()
    object EventStream : TracyHttpResponseBody()
    object Empty : TracyHttpResponseBody()
}

@InternalTracyApi
fun TracyHttpResponseBody.asJson(): JsonElement? {
    return when (this) {
        is TracyHttpResponseBody.Json -> this.json
        else -> null
    }
}
