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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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
    private val client: OkHttpClient = OkHttpClient(),
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
                        val result = uploadMediaFileToLangfuse(
                            params = LangfuseMediaUploadParams(
                                traceId = traceId,
                                field = field,
                                contentType = contentType,
                                data = data,
                            ), client = client, url = langfuseUrl, auth = langfuseBasicAuth
                        )
                        if (result.isFailure) {
                            logger.error(result.exceptionOrNull()) { "Failed to upload media file to $langfuseUrl for trace $traceId" }
                        }
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
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }

    private suspend fun uploadMediaFromUrl(
        traceId: String,
        field: String,
        url: String,
    ) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        val response = client.newCall(request).execute()
        val contentType = response.header("Content-Type")
        val data = response.body?.let {
            Base64.getEncoder().encodeToString(it.bytes())
        }

        if (contentType == null) {
            logger.warn { "Missing content type of media file at $url for trace $traceId" }
            return@withContext
        } else if (data == null) {
            logger.warn { "GET Response for $url doesn't contain body for trace $traceId" }
            return@withContext
        }

        val result = uploadMediaFileToLangfuse(
            params = LangfuseMediaUploadParams(
                traceId = traceId,
                field = field,
                contentType = contentType,
                data = data,
            ), client = client, url = langfuseUrl, auth = langfuseBasicAuth
        )
        if (result.isFailure) {
            logger.error(result.exceptionOrNull()) { "Failed to upload media file from $url for trace $traceId" }
        }
    }
}

/**
 * Uploads media content to Langfuse and links it to the given trace
 *
 * @see LangfuseMediaUploadParams
 */
private suspend fun uploadMediaFileToLangfuse(
    params: LangfuseMediaUploadParams,
    client: OkHttpClient,
    url: String,
    auth: String,
): Result<LangfuseMediaUploadResponse> = withContext(Dispatchers.IO) {
    val json = Json { ignoreUnknownKeys = true }

    // ensure that media type is valid
    val mediaType = params.contentType.toMediaType()
    val decodedBytes = Base64.getDecoder().decode(params.data)
    val sha256Hash = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(decodedBytes))

    // request upload URL from Langfuse
    /**
     * Get upload URL and media ID.
     *
     * See [Langfuse API for `/api/public/media`](https://api.reference.langfuse.com/#tag/media/post/api/public/media).
     */
    val requestBody = LangfuseMediaRequest(
        traceId = params.traceId,
        observationId = params.observationId,
        contentType = mediaType.toString(),
        contentLength = decodedBytes.size,
        sha256Hash = sha256Hash,
        field = params.field,
    )

    val request = Request.Builder()
        .url("$url/api/public/media")
        .post(json.encodeToString(requestBody).toRequestBody("application/json".toMediaType()))
        .addHeader("Authorization", "Basic $auth")
        .build()

    val response = client.newCall(request).execute()

    if (!response.isSuccessful) {
        return@withContext Result.failure(
            RequestFailedException(
                "Failed to request an upload url and media id from the endpoint $url/api/public/media, response code ${response.code}"
            )
        )
    }

    val uploadResource = response.body?.let {
        json.decodeFromString<LangfusePresignedUploadURL>(it.string())
    } ?: return@withContext Result.failure(
        IllegalStateException("Failed to parse upload resource from response")
    )

    // put the image to the upload URL
    if (uploadResource.uploadUrl != null) {
        // If there is no uploadUrl, the file was already uploaded
        val uploadRequest = Request.Builder()
            .url(uploadResource.uploadUrl)
            .put(decodedBytes.toRequestBody(mediaType))
            .build()

        val uploadResponse = client.newCall(uploadRequest).execute()

        if (!uploadResponse.isSuccessful) {
            return@withContext Result.failure(
                RequestFailedException(
                    "Failed to upload a media file, response code ${uploadResponse.code}"
                )
            )
        }

        // update upload status
        val patchRequestBody = LangfuseMediaUploadDetailsRequest(
            uploadedAt = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            uploadHttpStatus = uploadResponse.code,
            uploadHttpError = if (!uploadResponse.isSuccessful) uploadResponse.message else null,
        )

        val patchRequest = Request.Builder()
            .url("$url/api/public/media/${uploadResource.mediaId}")
            .patch(json.encodeToString(patchRequestBody).toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Basic $auth")
            .build()

        val patchResponse = client.newCall(patchRequest).execute()

        if (!patchResponse.isSuccessful) {
            return@withContext Result.failure(
                RequestFailedException(
                    "Failed to patch a media file with id ${uploadResource.mediaId}, response code ${patchResponse.code}"
                )
            )
        }
    }

    // retrieving the media data from Langfuse,
    // see details here: https://api.reference.langfuse.com/#tag/media/get/api/public/media/{mediaId}
    val mediaDataRequest = Request.Builder()
        .url("$url/api/public/media/${uploadResource.mediaId}")
        .get()
        .addHeader("Authorization", "Basic $auth")
        .build()

    val mediaDataResponse = client.newCall(mediaDataRequest).execute()

    if (!mediaDataResponse.isSuccessful) {
        return@withContext Result.failure(
            RequestFailedException(
                "Failed to retrieve a media file with id ${uploadResource.mediaId}, response code ${mediaDataResponse.code}"
            )
        )
    }

    val result = mediaDataResponse.body?.let {
        json.decodeFromString<LangfuseMediaUploadResponse>(it.string())
    } ?: return@withContext Result.failure(
        IllegalStateException("Failed to parse media data from response")
    )

    return@withContext Result.success(result)
}

private class RequestFailedException(message: String) : RuntimeException(message)
