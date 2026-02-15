/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package ai.jetbrains.tracy.core.http.parsers

import ai.jetbrains.tracy.core.http.protocol.ContentType
import ai.jetbrains.tracy.core.http.protocol.toContentType
import mu.KotlinLogging
import okhttp3.MediaType
import okio.Buffer
import org.apache.james.mime4j.parser.AbstractContentHandler
import org.apache.james.mime4j.parser.MimeStreamParser
import org.apache.james.mime4j.stream.BodyDescriptor
import org.apache.james.mime4j.stream.Field
import org.apache.james.mime4j.stream.MimeConfig
import java.io.InputStream
import java.nio.charset.Charset

/**
 * Parses a `multipart/form-data` HTTP request body.
 */
class MultipartFormDataParser {
    /**
     * Parses a multipart/form-data HTTP request body from the given content type and buffer.
     * This method extracts the MIME-formatted parts from the provided buffer,
     * expecting the given [contentType] to be of `multipart/form-data`.
     *
     * Automatically decodes fields based on their Content-Transfer-Encoding header (see [MimeStreamParser.isContentDecoding]).
     *
     * @param contentType The content type of the input data, used to properly parse the content.
     * @param bytes An array containing the `multipart/form-data` content to be parsed.
     * @throws IllegalArgumentException if the provided content type is not multipart/form-data.
     */
    fun parse(contentType: ContentType, bytes: ByteArray): FormData {
        checkContentType(contentType)

        val config = MimeConfig.custom()
            .setHeadlessParsing(contentType.asString())
            .setMaxLineLen(-1)
            .setMaxHeaderLen(-1)
            .build()
        val parser = MimeStreamParser(config)
        // recursively parsing RFC822 parts
        parser.setRecurse()
        parser.isContentDecoding = true

        val handler = MultipartContentHandler()
        parser.setContentHandler(handler)

        parser.parse(bytes.inputStream())
        return FormData(handler.parts)
    }

    /**
     * An overload of [MultipartFormDataParser.parse]
     *
     * @see MultipartFormDataParser.parse
     */
    fun parse(mediaType: MediaType, bytes: ByteArray) = parse(
        mediaType.toContentType(),
        bytes,
    )

    /**
     * An overload of [MultipartFormDataParser.parse]
     *
     * @see MultipartFormDataParser.parse
     */
    fun parse(mediaType: MediaType, buffer: Buffer) = parse(
        mediaType.toContentType(),
        buffer.readByteArray(),
    )

    /**
     * Requires a [contentType] to be of `multipart/form-data` with
     * a non-blank **"boundary"** parameter present.
     *
     * @throws IllegalArgumentException
     */
    private fun checkContentType(contentType: ContentType) {
        if (contentType.mimeType != ContentType.MultiPart.FormData.mimeType) {
            throw IllegalArgumentException(
                "Content type must be ${ContentType.MultiPart.FormData.mimeType}, got ${contentType.asString()}."
            )
        }

        // require 'boundary' parameter to be present
        val boundary = contentType.parameter("boundary")
        if (boundary.isNullOrBlank()) {
            throw IllegalArgumentException("Content type must contain non-blank 'boundary' parameter, got ${contentType.asString()}.")
        }
    }
}

/**
 * Represents a part of a form-data entity, typically used in multipart requests.
 *
 * @property name The name of the form field associated with this part. Can be null if not provided.
 * @property filename The filename associated with this part, commonly used for file uploads. Can be null if not applicable.
 * @property contentType The MIME type of the content associated with this part. Can be null if not specified.
 * @property headers additional headers present in the form part (e.g., Content-Transfer-Encoding, Content-ID, custom headers).
 * @property content The raw content of the form part.
 */
data class FormPart(
    val name: String?,
    val filename: String? = null,
    val contentType: ContentType? = null,
    val headers: Map<String, String> = emptyMap(),
    val content: ByteArray,
) {
    override fun toString(): String {
        val contentStr = when (contentType) {
            null -> content.decodeToString()
            else -> {
                val charset = contentType.charset() ?: when(contentType.mimeType) {
                    "text/plain" -> Charsets.US_ASCII
                    else -> Charsets.UTF_8
                }
                content.toString(charset)
            }
        }.let {
            if (it.length > 100) it.substring(0..97) + "..." else it
        }

        return "FormPart(name=$name, filename=$filename, contentType=${contentType?.asString()}, headers=$headers, content=`$contentStr`)"
    }
}

/**
 * Represents the parsed contents of a `multipart/form-data` HTTP request body.
 *
 * This data class encapsulates a collection of individual form parts, each represented
 * by the [FormPart] data class.
 *
 * @property parts A list of individual form parts, where each part contains metadata and raw content.
 */
data class FormData(val parts: List<FormPart>)

/**
 * Handles parsing of `multipart/form-data` content into individual form parts.
 *
 * Key behavior:
 * - Parses the `Content-Disposition` header to extract the field name and optional filename.
 * - Parses the `Content-Type` header to determine the MIME type of the current part.
 * - Maintains a list of all parsed parts in [parts], which includes the metadata and binary content.
 *
 * Inherits:
 * - [AbstractContentHandler]: Provides the abstract methods `field` and `body` to handle MIME message parts.
 *
 * Properties:
 * - `parts`: A mutable list of [FormPart] that holds the parsed data from the multipart content.
 */
private class MultipartContentHandler : AbstractContentHandler() {
    val parts = mutableListOf<FormPart>()

    private var currentContext = PartContext()

    /**
     * Marks whether the very first field, which is always a content type of the entire form data
     * (i.e., `multipart/form-data`), already encountered.
     *
     * Note: We need to skip the first content type, as it corresponds to the form-data body as a whole,
     * not an individual field (see [MultipartContentHandler.field]).
     */
    private var firstFieldEncountered = false

    override fun field(field: Field) {
        when (field.name.lowercase()) {
            "content-disposition" -> {
                // parse: `form-data; name="field-name"; filename="file.txt"`
                // for HTTP requests, no other values for this header allowed
                // see: https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Content-Disposition
                val value = field.body
                // matching: `name="my-name"` or `name=my-name`
                val nameMatch = Regex("""name=(?:"([^"]+)"|([^\s;]+))""").find(value)
                // matching: `filename="my-file.txt"` or `filename=my-file.txt`
                val filenameMatch = Regex("""filename=(?:"([^"]+)"|([^\s;]+))""").find(value)

                // group 1 corresponds to the quoted field value, group 2 corresponds to the unquoted field value.
                // dropping first value because it is an entire match
                currentContext.name = nameMatch?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }
                currentContext.filename = filenameMatch?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }
            }

            "content-type" -> {
                // the very first encountered field is a content type of the entire body;
                // therefore, we skip it
                val contentType = parseContentType(contentType = field.body)
                if (contentType == null) {
                    logger.warn("Failed to parse Content-Type header: ${field.body}")
                }

                // if this field is the first one encountered, and it is a `multipart/form-data`,
                // then it is the content type of the entire body, and we skip it;
                // otherwise, it is a content type of the body part's field.
                val isBodyContentTypeFieldEncountered = !firstFieldEncountered &&
                        contentType?.mimeType == ContentType.MultiPart.FormData.mimeType
                if (!isBodyContentTypeFieldEncountered) {
                    // if not, it is a content type of the actual field of a body part;
                    // otherwise, ignore to skip it
                    currentContext.contentType = contentType
                }
            }

            else -> {
                // collect all other headers
                currentContext.headers[field.name] = field.body
            }
        }
        // mark that the first field already encountered
        firstFieldEncountered = true
    }

    override fun body(bd: BodyDescriptor, inputStream: InputStream) {
        val content = inputStream.readBytes()

        when (currentContext.name) {
            null -> {
                logger.warn { "No name found for multipart part of content type ${currentContext.contentType}, skipping" }
            }

            else -> {
                // add a new part into form data
                val part = FormPart(
                    name = currentContext.name,
                    filename = currentContext.filename,
                    contentType = currentContext.contentType,
                    headers = currentContext.headers,
                    content = content,
                )
                parts.add(part)
            }
        }
        // reset for the next part
        currentContext = PartContext()
    }

    /**
     * Parses the given content type string and returns a representation of the media type
     * and its associated parameters, if valid.
     *
     * @param contentType the content type string to parse
     * @return a ContentType instance representing the parsed type, subtype, and parameters,
     *         or null if the content type is invalid or cannot be parsed
     */
    private fun parseContentType(contentType: String): ContentType? {
        val trimmed = contentType.trim()
        if (trimmed.isEmpty()) {
            return null
        }

        // split media type from parameters
        val parts = trimmed.split(';')
        val mediaType = parts[0].trim()

        // parse type/subtype
        val slashIndex = mediaType.indexOf('/')
        if (slashIndex <= 0 || slashIndex >= mediaType.length - 1) {
            return null
        }

        val type = mediaType.substring(0, slashIndex).trim().lowercase()
        val subtype = mediaType.substring(slashIndex + 1).trim().lowercase()

        if (type.isEmpty() || subtype.isEmpty()) {
            return null
        }

        // parse parameters (`name=value` or name="quoted value")
        val parameters = mutableMapOf<String, String>()

        for (i in 1 until parts.size) {
            val param = parts[i].trim()
            val eqIndex = param.indexOf('=')
            if (eqIndex <= 0) {
                continue
            }

            val name = param.substring(0, eqIndex).trim().lowercase()
            var value = param.substring(eqIndex + 1).trim()

            // unquote if needed
            if (value.startsWith('"') && value.endsWith('"') && value.length >= 2) {
                value = value.substring(1, value.length - 1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
            }

            parameters[name] = value
        }

        return object : ContentType {
            override val type = type
            override val subtype = subtype

            override fun parameter(name: String) = parameters[name.lowercase()]

            override fun charset() = parameters["charset"]?.let {
                try { Charset.forName(it) } catch (_: Exception) { null }
            }

            override fun asString(): String {
                val params = parameters.entries.joinToString("; ") { (k, v) ->
                    if (v.any { it in " ;\"" }) {
                        val value = v
                            .replace("\\", "\\\\")
                            .replace("\"", "\\\"")

                        "$k=\"$value\""
                    }
                    else {
                        "$k=$v"
                    }
                }

                return if (params.isEmpty()) {
                    "$type/$subtype"
                } else {
                    "$type/$subtype; $params"
                }
            }
        }
    }

    private data class PartContext(
        var name: String? = null,
        var filename: String? = null,
        var contentType: ContentType? = null,
        var headers: MutableMap<String, String> = mutableMapOf(),
    )

    private val logger = KotlinLogging.logger {}
}