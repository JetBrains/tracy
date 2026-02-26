/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.core.exporters.langfuse

import org.jetbrains.ai.tracy.core.adapters.media.SupportedMediaContentTypes
import org.jetbrains.ai.tracy.core.adapters.media.UploadableMediaContentAttributeKeys
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.ReadWriteSpan
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.SpanProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import mu.KotlinLogging
import java.security.MessageDigest
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Extracts attributes of media content attached to the span
 * and uploads it to Langfuse linking to the given trace.
 *
 * Allows viewing of media content on Langfuse UI.
 *
 * @see UploadableMediaContentAttributeKeys
 * @see uploadMediaFileToLangfuse
 */
internal class LangfuseMediaSpanProcessor(
    private val scope: CoroutineScope,
    private val client: HttpClient = HttpClient {
        install(ContentNegotiation) {
            json()
        }
    },
    private val langfuseUrl: String,
    private val langfuseBasicAuth: String,
) : SpanProcessor {
    private val isClosed = AtomicBoolean(false)

    companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun onStart(parentContext: Context, span: ReadWriteSpan) {}

    override fun isStartRequired(): Boolean = false

    override fun onEnd(span: ReadableSpan) {
        val traceId = span.spanContext.traceId

        var index = 0
        while (span.attributes.get(UploadableMediaContentAttributeKeys.forIndex(index).type) != null) {
            val keys = UploadableMediaContentAttributeKeys.forIndex(index)

            val type = span.attributes.get(keys.type)
            val field =
                span.attributes.get(keys.field) ?: error("Field attribute not found for media item at index $index")

            when (type) {
                SupportedMediaContentTypes.URL.type -> {
                    val url =
                        span.attributes.get(keys.url) ?: error("URL attribute not found for media item at index $index")
                    scope.launch { uploadMediaFromUrl(traceId, field, url) }
                }

                SupportedMediaContentTypes.BASE64.type -> {
                    val contentType = span.attributes.get(keys.contentType)
                        ?: error("Content type attribute not found for media item at index $index")
                    val data = span.attributes.get(keys.data)
                        ?: error("Data attribute not found for media item at index $index")

                    scope.launch {
                        uploadMediaFileToLangfuse(
                            params = LangfuseMediaUploadParams(
                                traceId = traceId,
                                field = field,
                                contentType = contentType,
                                data = data,
                            ), client = client, url = langfuseUrl, auth = langfuseBasicAuth
                        )
                    }
                }

                else -> error("Unsupported media content type '$type'")
            }

            ++index
        }
    }

    override fun isEndRequired(): Boolean = true

    override fun shutdown(): CompletableResultCode {
        closeClient()
        return CompletableResultCode.ofSuccess()
    }

    override fun close() {
        closeClient()
    }

    private fun closeClient() {
        if (isClosed.compareAndSet(false, true)) {
            client.close()
        }
    }

    private suspend fun uploadMediaFromUrl(
        traceId: String,
        field: String,
        url: String,
    ) {
        val response = client.get(url)
        val contentType = response.headers[HttpHeaders.ContentType]
        val data = Base64.getEncoder().encodeToString(response.bodyAsBytes())

        if (contentType == null) {
            logger.warn { "Missing content type of media file at $url for trace $traceId" }
            return
        }

        uploadMediaFileToLangfuse(
            params = LangfuseMediaUploadParams(
                traceId = traceId,
                field = field,
                contentType = contentType,
                data = data,
            ), client = client, url = langfuseUrl, auth = langfuseBasicAuth
        )
    }
}

/**
 * Uploads media content to Langfuse and links it to the given trace
 *
 * @see LangfuseMediaUploadParams
 */
private suspend fun uploadMediaFileToLangfuse(
    params: LangfuseMediaUploadParams,
    client: HttpClient,
    url: String,
    auth: String,
): Result<LangfuseMediaUploadResponse> {
    // ensure that media type is valid
    val contentType = ContentType.parse(params.contentType)
    val decodedBytes = Base64.getDecoder().decode(params.data)
    val sha256Hash = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(decodedBytes))

    // request upload URL from Langfuse
    /**
     * Get upload URL and media ID.
     *
     * See [Langfuse API for `/api/public/media`](https://api.reference.langfuse.com/#tag/media/post/api/public/media).
     */
    val response = client.post("$url/api/public/media") {
        contentType(ContentType.Application.Json)
        header(HttpHeaders.Authorization, "Basic $auth")

        val request = LangfuseMediaRequest(
            traceId = params.traceId,
            observationId = params.observationId,
            contentType = contentType.toString(),
            contentLength = decodedBytes.size,
            sha256Hash = sha256Hash,
            field = params.field,
        )
        setBody(request)
    }

    if (!response.status.isSuccess()) {
        return Result.failure(
            RequestFailedException(
                "Failed to request an upload url and media id from the endpoint $url/api/public/media, response code ${response.status.value}"
            )
        )
    }

    val uploadResource = response.body<LangfusePresignedUploadURL>()

    // put the image to the upload URL
    if (uploadResource.uploadUrl != null) {
        // If there is no uploadUrl, the file was already uploaded
        val uploadResponse = client.put(uploadResource.uploadUrl) {
            // the content type of the media being uploaded
            contentType(contentType)
            setBody(decodedBytes)
        }

        if (!uploadResponse.status.isSuccess()) {
            return Result.failure(
                RequestFailedException(
                    "Failed to upload a media file, response code ${uploadResponse.status.value}"
                )
            )
        }

        // update upload status
        val patchResponse = client.patch("$url/api/public/media/${uploadResource.mediaId}") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Basic $auth")

            val request = LangfuseMediaUploadDetailsRequest(
                uploadedAt = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                uploadHttpStatus = uploadResponse.status.value,
                uploadHttpError = if (!uploadResponse.status.isSuccess()) uploadResponse.status.description else null,
            )
            setBody(request)
        }

        if (!patchResponse.status.isSuccess()) {
            return Result.failure(
                RequestFailedException(
                    "Failed to patch a media file with id ${uploadResource.mediaId}, response code ${patchResponse.status.value}"
                )
            )
        }
    }

    // retrieving the media data from Langfuse,
    // see details here: https://api.reference.langfuse.com/#tag/media/get/api/public/media/{mediaId}
    val mediaDataResponse = client.get("$url/api/public/media/${uploadResource.mediaId}") {
        header(HttpHeaders.Authorization, "Basic $auth")
    }

    if (!mediaDataResponse.status.isSuccess()) {
        return Result.failure(
            RequestFailedException(
                "Failed to retrieve a media file with id ${uploadResource.mediaId}, response code ${mediaDataResponse.status.value}"
            )
        )
    }

    return Result.success(mediaDataResponse.body<LangfuseMediaUploadResponse>())
}

private class RequestFailedException(message: String) : RuntimeException(message)
