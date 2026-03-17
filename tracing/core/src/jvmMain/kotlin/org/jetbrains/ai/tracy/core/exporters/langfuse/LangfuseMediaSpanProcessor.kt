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
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.coroutines.executeAsync
import org.jetbrains.ai.tracy.core.adapters.media.SupportedMediaContentTypes
import org.jetbrains.ai.tracy.core.adapters.media.UploadableMediaContentAttributeKeys
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
    private val langfuseUrl: String,
    private val langfuseBasicAuth: String,
) : SpanProcessor {
    private val client = OkHttpClient()
    // flag indicating whether the owned client has been closed
    private val isClientClosed = AtomicBoolean(false)

    // tracks in-flight upload jobs to allow graceful shutdown
    private val activeJobs: MutableList<Job> = Collections.synchronizedList(mutableListOf())

    // dedicated scope for the shutdown coroutine, separate from `scope` so that external
    // cancellation of `scope` does not prevent a graceful shutdown from completing
    private val shutdownScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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

                    val uploadFromUrlJob = scope.launch {
                        val result = uploadMediaFromUrl(traceId, field, url)
                        if (result.isFailure) {
                            logger.error(result.exceptionOrNull()) {
                                "Failed to upload media file from $url for trace $traceId" }
                        }
                    }
                    trackJob(uploadFromUrlJob)
                }

                SupportedMediaContentTypes.BASE64.type -> {
                    val contentType = span.attributes.get(keys.contentType)
                        ?: error("Content type attribute not found for media item at index $index")
                    val data = span.attributes.get(keys.data)
                        ?: error("Data attribute not found for media item at index $index")

                    val uploadFromBase64Job = scope.launch {
                        val result = uploadMediaFromBase64(traceId, field, contentType, data)
                        if (result.isFailure) {
                            logger.error(result.exceptionOrNull()) {
                                "Failed to upload media file to $langfuseUrl for trace $traceId" }
                        }
                    }
                    trackJob(uploadFromBase64Job)
                }

                else -> error("Unsupported media content type '$type'")
            }

            ++index
        }
    }

    override fun isEndRequired(): Boolean = true

    override fun shutdown(): CompletableResultCode {
        val result = CompletableResultCode()
        shutdownScope.launch {
            try {
                awaitActiveJobs()
                closeClient()
                result.succeed()
            } catch (err: Throwable) {
                logger.error(err) { "Failed to shutdown Langfuse media span processor" }
                result.failExceptionally(err)
            } finally {
                shutdownScope.cancel()
            }
        }
        return result
    }

    override fun close() {
        // the default implementation calls `shutdown` with a timed join
        super.close()
    }

    private fun trackJob(job: Job) {
        synchronized(activeJobs) {
            activeJobs.add(job)
        }
        job.invokeOnCompletion {
            synchronized(activeJobs) {
                activeJobs.remove(job)
            }
        }
    }

    private suspend fun awaitActiveJobs() {
        // repeatedly snapshot and await jobs until no active jobs remain
        while (true) {
            // snapshot the list under synchronization to avoid concurrent modification
            val jobsSnapshot = synchronized(activeJobs) { activeJobs.toList() }
            if (jobsSnapshot.isEmpty()) {
                break
            }

            for (job in jobsSnapshot) {
                try {
                    job.join()
                } catch (_: CancellationException) {
                    // The upload job was canceled (e.g., the outer scope was canceled before shutdown).
                    // There is nothing left to await for this job.
                }
            }
        }
    }

    private fun closeClient() {
        if (isClientClosed.compareAndSet(false, true)) {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }

    /**
     * Executes the following workflow:
     * 1. Downloads the media file under the given URL (GET request)
     * 2. Executes [uploadMediaFileToLangfuse] to upload the media file to Langfuse
     */
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

            val (contentType, data) = client.newCall(request).executeAsync().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(IllegalStateException(
                        "Failed to GET media file from $url for trace $traceId, response code ${response.code}"))
                }

                val contentType = response.header("Content-Type")
                val data = response.body.bytes().let {
                    Base64.getEncoder().encodeToString(it)
                }
                contentType to data
            }

            if (contentType == null) {
                return Result.failure(IllegalStateException(
                    "Missing content type of media file at $url for trace $traceId"))
            }

            return uploadMediaFileToLangfuse(
                params = LangfuseMediaUploadParams(
                    traceId = traceId,
                    field = field,
                    contentType = contentType,
                    data = data,
                ),
                langfuseUrl = langfuseUrl,
                langfuseBasicAuth = langfuseBasicAuth,
            )
        } catch (err: Exception) {
            return Result.failure(err)
        }
    }

    /**
     * Directly executes [uploadMediaFileToLangfuse] to upload the media file to Langfuse.
     */
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
                ),
                langfuseUrl = langfuseUrl,
                langfuseBasicAuth = langfuseBasicAuth,
            )
        } catch (err: Exception) {
            Result.failure(err)
        }
    }

    /**
     * Uploads media content to Langfuse and links it to the given trace.
     *
     * **Implementation Details**
     *
     * The executed workflow is as follows:
     * 1. `POST /api/public/media`: Retrieves the upload URL and media id for the media content sending its SHA256-hash
     *    (receives optional `uploadUrl` and `mediaId`)
     * 2. If `uploadUrl` isn't null, then the media content hasn't been uploaded on this Langfuse instance before.
     *    Upload the media content by calling [uploadBytesByUploadUrl].
     * 3. `GET /api/public/media/{mediaId}`: Retrieve the URL under which Langfuse saved the media content.
     *
     * @see LangfuseMediaUploadParams
     */
    private suspend fun uploadMediaFileToLangfuse(
        params: LangfuseMediaUploadParams,
        langfuseUrl: String,
        langfuseBasicAuth: String,
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
            .url("$langfuseUrl/api/public/media")
            .post(json.encodeToString(requestBody).toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Basic $langfuseBasicAuth")
            .build()

        val uploadResource = client.newCall(request).executeAsync().use { response ->
            if (!response.isSuccessful) {
                return Result.failure(
                    RequestFailedException(
                        "Failed to request an upload url and media id from the endpoint $langfuseUrl/api/public/media, response code ${response.code}"
                    )
                )
            }

            response.body.string().let {
                json.decodeFromString<LangfusePresignedUploadURL>(it)
            }
        }

        // put the image to the upload URL
        if (uploadResource.uploadUrl != null) {
            // If uploadUrl is present, we need to upload the file (otherwise it was already uploaded)
            val result = uploadBytesByUploadUrl(
                langfuseUrl = langfuseUrl,
                langfuseBasicAuth = langfuseBasicAuth,
                bytes = decodedBytes,
                mediaType = mediaType,
                uploadResource = uploadResource,
            )
            if (result.isFailure) {
                logger.error(result.exceptionOrNull()) {
                    "Encountered error(s) during upload of a media file to Langfuse"
                }
            } else if (result.isSuccess) {
                logger.debug { "Successfully uploaded media file to Langfuse" }
            }
        }

        // retrieving the media data from Langfuse,
        // see details here: https://api.reference.langfuse.com/#tag/media/get/api/public/media/{mediaId}
        val mediaDataRequest = Request.Builder()
            .url("$langfuseUrl/api/public/media/${uploadResource.mediaId}")
            .get()
            .addHeader("Authorization", "Basic $langfuseBasicAuth")
            .build()

        // even if the media upload above failed, we still
        // attempt to get the resource by its media id
        val result = client.newCall(mediaDataRequest).executeAsync().use { mediaDataResponse ->
            if (!mediaDataResponse.isSuccessful) {
                return Result.failure(
                    RequestFailedException(
                        "Failed to retrieve a media file with id ${uploadResource.mediaId}, response code ${mediaDataResponse.code}"
                    )
                )
            }

            mediaDataResponse.body.string().let {
                json.decodeFromString<LangfuseMediaUploadResponse>(it)
            }
        }

        return Result.success(result)
    }

    /**
     * Uploads the given bytes to the upload URL obtained from Langfuse.
     *
     * The executed workflow is as follows:
     * 1. `PUT {uploadUrl}`: uploads the given bytes to the upload URL obtained from Langfuse.
     * 2. `PATCH /api/public/media/{mediaId}`: updates the upload status of the media content
     *     (This patch update indicates to Langfuse whether the previous PUT upload succeeded or failed.
     */
    private suspend fun uploadBytesByUploadUrl(
        langfuseUrl: String,
        langfuseBasicAuth: String,
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

        client.newCall(uploadRequest).executeAsync().use { uploadResponse ->
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
                .url("$langfuseUrl/api/public/media/${uploadResource.mediaId}")
                .patch(
                    json.encodeToString(patchRequestBody)
                        .toRequestBody("application/json".toMediaType())
                )
                .addHeader("Authorization", "Basic $langfuseBasicAuth")
                .build()

            client.newCall(patchRequest).executeAsync().use { patchResponse ->
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
}

private val logger = KotlinLogging.logger {}
private val json = Json { ignoreUnknownKeys = true }

private class RequestFailedException(message: String) : RuntimeException(message)
