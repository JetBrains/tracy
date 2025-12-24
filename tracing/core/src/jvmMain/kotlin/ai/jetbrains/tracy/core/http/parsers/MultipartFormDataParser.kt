package ai.jetbrains.tracy.core.http.parsers

import ai.jetbrains.tracy.core.http.protocol.toContentType
import okhttp3.MediaType
import okio.Buffer
import org.apache.james.mime4j.parser.AbstractContentHandler
import org.apache.james.mime4j.parser.MimeStreamParser
import org.apache.james.mime4j.stream.BodyDescriptor
import org.apache.james.mime4j.stream.Field
import org.apache.james.mime4j.stream.MimeConfig
import io.ktor.http.ContentType
import io.ktor.http.charset
import mu.KotlinLogging
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.SequenceInputStream

/**
 * Parses a `multipart/form-data` HTTP request body.
 */
class MultipartFormDataParser {
    /**
     * Parses a multipart/form-data HTTP request body from the given content type and buffer.
     * This method extracts the MIME-formatted parts from the provided buffer,
     * expecting the given [contentType] to be of `multipart/form-data`.
     *
     * @param contentType The content type of the input data, used to properly parse the content.
     * @param bytes An array containing the `multipart/form-data` content to be parsed.
     * @throws IllegalArgumentException if the provided content type is not multipart/form-data.
     */
    fun parse(contentType: ContentType, bytes: ByteArray): FormData {
        checkContentType(contentType)

        val parser = MimeStreamParser(MimeConfig.DEFAULT)
        val handler = MultipartContentHandler()
        parser.setContentHandler(handler)

        // use headless parsing since we don't have full MIME headers,
        // we need to prepend the Content-Type header for the parser
        val headerPrefix = "Content-Type: $contentType\r\n\r\n"

        val combinedStream = SequenceInputStream(
            ByteArrayInputStream(headerPrefix.toByteArray()),
            bytes.inputStream(),
        )

        parser.parse(combinedStream)
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

    companion object {
        private fun checkContentType(contentType: ContentType) {
            val contentType = contentType.withoutParameters()

            if (!ContentType.MultiPart.FormData.match(contentType)) {
                throw IllegalArgumentException("Content type must be ${ContentType.MultiPart.FormData}, got $contentType.")
            }
        }
    }
}

/**
 * Represents a part of a form-data entity, typically used in multipart requests.
 *
 * @property name The name of the form field associated with this part. Can be null if not provided.
 * @property filename The filename associated with this part, commonly used for file uploads. Can be null if not applicable.
 * @property content The raw binary content of the form part.
 * @property contentType The MIME type of the content associated with this part. Can be null if not specified.
 */
data class FormPart(
    val name: String?,
    val filename: String? = null,
    val content: ByteArray,
    val contentType: ContentType? = null
) {
    override fun toString(): String {
        val contentStr = when (contentType) {
            null -> super.toString()
            else -> {
                content.toString(contentType.charset() ?: Charsets.UTF_8)
            }
        }.take(100)

        return "FormPart(name=$name, filename=$filename, contentType=$contentType, content=`$contentStr`)"
    }
}

/**
 * Represents the parsed contents of a multipart/form-data HTTP request body.
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
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    val parts = mutableListOf<FormPart>()
    private var currentPartName: String? = null
    private var currentFilename: String? = null
    private var currentContentType: ContentType? = null

    override fun field(field: Field) {
        val fieldName = field.name.lowercase()
        println("fieldName: $fieldName")

        when (fieldName) {
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
                currentPartName = nameMatch?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }
                currentFilename = filenameMatch?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }
            }
            "content-type" -> {
                currentContentType = try {
                    ContentType.parse(field.body)
                }
                catch (err: Exception) {
                    logger.trace("Failed to parse Content-Type header: ${field.body}", err)
                    null
                }
            }
        }
    }

    override fun body(bd: BodyDescriptor, inputStream: InputStream) {
        val content = inputStream.readBytes()

        parts.add(
            FormPart(
                name = currentPartName,
                filename = currentFilename,
                content = content,
                contentType = currentContentType
            )
        )

        // Reset for the next part
        currentPartName = null
        currentFilename = null
        currentContentType = null
    }
}