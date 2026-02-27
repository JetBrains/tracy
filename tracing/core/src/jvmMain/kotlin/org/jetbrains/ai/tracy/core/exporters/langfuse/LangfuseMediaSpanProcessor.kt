/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.core.exporters.langfuse

import io.opentelemetry.context.Context
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.ReadWriteSpan
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.SpanProcessor
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.jetbrains.ai.tracy.core.adapters.media.SupportedMediaContentTypes
import org.jetbrains.ai.tracy.core.adapters.media.UploadableMediaContentAttributeKeys
import java.io.IOException
import java.security.MessageDigest
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Extension function to convert OkHttp's callback-based async API to Kotlin suspend functions.
 * Provides proper coroutine cancellation support and non-blocking HTTP operations.
 */
private suspend fun Call.await(): Response {
    return suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation {
            cancel()
        }
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response)
            }

            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }
        })
    }
}

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
    // flag indicating whether the client has been closed
    private val isClientClosed = AtomicBoolean(false)

    override fun onStart(parentContext: Context, span: ReadWriteSpan) {}

    override fun isStartRequired(): Boolean = false

    override fun onEnd(span: ReadableSpan) {
        val traceId = span.spanContext.traceId

        var index = 0
        while (span.attributes.get(UploadableMediaContentAttributeKeys.forIndex(index).type) != null) {
            val keys = UploadableMediaContentAttributeKeys.forIndex(index)

            val type = span.attributes.get(keys.type)
            val field = span.attributes.get(keys.field)
                ?: error("Field attribute not found for media item at index $index")

            when (type) {
                SupportedMediaContentTypes.URL.type -> {
                    val url = span.attributes.get(keys.url)
                        ?: error("URL attribute not found for media item at index $index")
                    scope.launch {
                        val result = uploadMediaFromUrl(traceId, field, url)
                        if (result.isFailure) {
                            logger.error(result.exceptionOrNull()) {
                                "Failed to upload media file from $url for trace $traceId" }
                        }
                    }
                }

                SupportedMediaContentTypes.BASE64.type -> {
                    val contentType = span.attributes.get(keys.contentType)
                        ?: error("Content type attribute not found for media item at index $index")
                    val data = span.attributes.get(keys.data)
                        ?: error("Data attribute not found for media item at index $index")
                    scope.launch {
                        val result = uploadMediaFromBase64(traceId, field, contentType, data)
                        if (result.isFailure) {
                            logger.error(result.exceptionOrNull()) {
                                "Failed to upload media file to $langfuseUrl for trace $traceId" }
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
        if (isClientClosed.compareAndSet(false, true)) {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }

    private suspend fun uploadMediaFromUrl(
        traceId: String,
        field: String,
        url: String,
    ): Result<LangfuseMediaUploadResponse> {
        try {
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val (contentType, data) = client.newCall(request).await().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(IllegalStateException(
                        "Failed to GET media file from $url for trace $traceId, response code ${response.code}"))
                }

                val contentType = response.header("Content-Type")
                val data = response.body?.bytes()?.let {
                    Base64.getEncoder().encodeToString(it)
                }
                contentType to data
            }

            if (contentType == null) {
                return Result.failure(IllegalStateException(
                    "Missing content type of media file at $url for trace $traceId"))
            } else if (data == null) {
                return Result.failure(IllegalStateException(
                    "GET Response for $url doesn't contain body for trace $traceId"))
            }

            return uploadMediaFileToLangfuse(
                params = LangfuseMediaUploadParams(
                    traceId = traceId,
                    field = field,
                    contentType = contentType,
                    data = data,
                ), client = client, url = langfuseUrl, auth = langfuseBasicAuth
            )
        } catch (err: Exception) {
            return Result.failure(err)
        }
    }

    private suspend fun uploadMediaFromBase64(
        traceId: String,
        field: String,
        contentType: String,
        data: String,
    ): Result<LangfuseMediaUploadResponse> {
        return try {
            uploadMediaFileToLangfuse(
                params = LangfuseMediaUploadParams(
                    traceId = traceId,
                    field = field,
                    contentType = contentType,
                    data = data,
                ), client = client, url = langfuseUrl, auth = langfuseBasicAuth
            )
        } catch (err: Exception) {
            Result.failure(err)
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
    ): Result<LangfuseMediaUploadResponse> {
        // ensure that the media type is valid and compute hash (CPU-bound operations)
        val (decodedBytes, sha256Hash) = try {
            withContext(Dispatchers.Default) {
                val bytes = Base64.getDecoder().decode(params.data)
                val hash = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(bytes))
                bytes to hash
            }
        } catch (err: IllegalArgumentException) {
            return Result.failure(IllegalArgumentException(
                "Failed to decode Base64 media data for upload to Langfuse. Data may contain invalid Base64 characters", err)
            )
        }

        // request upload URL from Langfuse

        // parsing media type from string to ensure it's valid
        val mediaType = try {
            params.contentType.toMediaType()
        } catch (err: IllegalArgumentException) {
            return Result.failure(IllegalArgumentException(
                "Invalid content type '${params.contentType}' for Langfuse media upload",
                err,
            ))
        }
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

        val uploadResource = client.newCall(request).await().use { response ->
            if (!response.isSuccessful) {
                return Result.failure(
                    RequestFailedException(
                        "Failed to request an upload url and media id from the endpoint $url/api/public/media, response code ${response.code}"
                    )
                )
            }

            response.body?.string()?.let {
                json.decodeFromString<LangfusePresignedUploadURL>(it)
            } ?: return Result.failure(
                IllegalStateException("Failed to parse upload resource from response")
            )
        }

        // put the image to the upload URL
        if (uploadResource.uploadUrl != null) {
            // If there is no uploadUrl, the file was already uploaded
            val result = uploadBytesByUploadUrl(
                url = url,
                auth = auth,
                bytes = decodedBytes,
                mediaType = mediaType,
                uploadResource = uploadResource,
            )
            if (result.isFailure) {
                logger.error(result.exceptionOrNull()) {
                    "Encountered error(s) during upload of a media file to Langfuse"
                }
            } else if (result.isSuccess) {
                logger.info { "Successfully uploaded media file to Langfuse" }
            }
        }

        // retrieving the media data from Langfuse,
        // see details here: https://api.reference.langfuse.com/#tag/media/get/api/public/media/{mediaId}
        val mediaDataRequest = Request.Builder()
            .url("$url/api/public/media/${uploadResource.mediaId}")
            .get()
            .addHeader("Authorization", "Basic $auth")
            .build()

        val result = client.newCall(mediaDataRequest).await().use { mediaDataResponse ->
            if (!mediaDataResponse.isSuccessful) {
                return Result.failure(
                    RequestFailedException(
                        "Failed to retrieve a media file with id ${uploadResource.mediaId}, response code ${mediaDataResponse.code}"
                    )
                )
            }

            mediaDataResponse.body?.string()?.let {
                json.decodeFromString<LangfuseMediaUploadResponse>(it)
            } ?: return Result.failure(IllegalStateException(
                    "Failed to parse media data from response or the response body is null")
            )
        }

        return Result.success(result)
    }

    private suspend fun uploadBytesByUploadUrl(
        url: String,
        auth: String,
        bytes: ByteArray,
        mediaType: MediaType,
        uploadResource: LangfusePresignedUploadURL,
    ): Result<Unit> {
        // if there is no uploadUrl, the file was already uploaded before
        if (uploadResource.uploadUrl == null) {
            return Result.success(Unit)
        }

        val uploadRequest = Request.Builder()
            .url(uploadResource.uploadUrl)
            .put(bytes.toRequestBody(mediaType))
            .build()

        val errorMessages = mutableListOf<String>()

        client.newCall(uploadRequest).await().use { uploadResponse ->
            if (!uploadResponse.isSuccessful) {
                errorMessages.add(
                    "Failed to upload a media file, response code ${uploadResponse.code}: ${uploadResponse.message}")
            }

            // update upload status
            val patchRequestBody = LangfuseMediaUploadDetailsRequest(
                uploadedAt = ZonedDateTime.now(ZoneOffset.UTC)
                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                uploadHttpStatus = uploadResponse.code,
                uploadHttpError = if (!uploadResponse.isSuccessful) uploadResponse.message else null,
            )

            val patchRequest = Request.Builder()
                .url("$url/api/public/media/${uploadResource.mediaId}")
                .patch(
                    json.encodeToString(patchRequestBody)
                        .toRequestBody("application/json".toMediaType())
                )
                .addHeader("Authorization", "Basic $auth")
                .build()

            client.newCall(patchRequest).await().use { patchResponse ->
                if (!patchResponse.isSuccessful) {
                    errorMessages.add(
                        "Failed to patch a media file with id ${uploadResource.mediaId}, response code ${patchResponse.code}: ${patchResponse.message}")
                }
            }
        }

        return if (errorMessages.isEmpty()) {
            Result.success(Unit)
        } else {
            Result.failure(RequestFailedException(
                errorMessages.joinToString("\n")
            ))
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
        private val json = Json { ignoreUnknownKeys = true }
    }
}

private class RequestFailedException(message: String) : RuntimeException(message)
