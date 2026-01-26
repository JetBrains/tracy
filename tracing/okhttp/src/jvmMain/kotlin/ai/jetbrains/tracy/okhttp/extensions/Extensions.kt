package ai.jetbrains.tracy.okhttp.extensions

import ai.jetbrains.tracy.core.http.parsers.MultipartFormDataParser
import ai.jetbrains.tracy.core.http.protocol.MediaType as TracyMediaType
import ai.jetbrains.tracy.core.http.protocol.ContentType as TracyContentType
import ai.jetbrains.tracy.core.http.protocol.Url
import okhttp3.HttpUrl
import okhttp3.MediaType
import okio.Buffer

fun HttpUrl.toProtocolUrl() = Url(scheme, host, pathSegments)

/**
 * An overload of [MultipartFormDataParser.parse]
 *
 * @see MultipartFormDataParser.parse
 */
fun MultipartFormDataParser.parse(mediaType: MediaType, buffer: Buffer) = parse(
    mediaType = mediaType.toMediaType(),
    bytes = buffer.readByteArray()
)

fun MultipartFormDataParser.parse(mediaType: MediaType, bytes: ByteArray) = parse(mediaType.toMediaType(), bytes)

fun MediaType.toMediaType() = TracyMediaType(
    mediaType = this.toString(),
    type = this.type,
    subtype = this.subtype,
)

fun MediaType.toContentType() = TracyContentType.parse(this.toString())